package com.traslation.backend.canal;

import com.traslation.backend.search.ProductSearchIndex;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Canal TCP binlog consumer. Acknowledges a batch only after every ES change succeeds. */
@Component
public class CanalCdcWorker {
    private static final Logger LOG = Logger.getLogger(CanalCdcWorker.class.getName());
    private static final List<String> LANGS =
            List.of("zh", "en", "ar", "fr", "de", "ja", "ko", "ru", "th", "vi", "ms", "id", "tl");
    private final JdbcTemplate db;
    private final RestClient es;
    private final ProductSearchIndex searchIndex;
    private final String index, host, destination, user, password, clientId, filter;
    private final int port, batchSize, timeoutMs;
    private final boolean enabled;
    private final AtomicBoolean stop = new AtomicBoolean();
    private final AtomicReference<Socket> socket = new AtomicReference<>();
    private final AtomicReference<String> error = new AtomicReference<>();
    private final AtomicReference<String> lastEvent = new AtomicReference<>();
    private final AtomicLong batches = new AtomicLong(),
            inserted = new AtomicLong(),
            updated = new AtomicLong(),
            deleted = new AtomicLong(),
            images = new AtomicLong(),
            skipped = new AtomicLong();
    private volatile boolean connected;
    private volatile Instant lastSuccess;
    private Thread thread;

    public CanalCdcWorker(
            JdbcTemplate db,
            RestClient.Builder builder,
            ProductSearchIndex searchIndex,
            @Value("${app.search.url:http://localhost:9200}") String esUrl,
            @Value("${app.search.index:product}") String index,
            @Value("${app.search.username:}") String esUser,
            @Value("${app.search.password:}") String esPassword,
            @Value("${app.canal.enabled:false}") boolean enabled,
            @Value("${app.canal.host:localhost}") String host,
            @Value("${app.canal.port:11111}") int port,
            @Value("${app.canal.destination:product_sync}") String destination,
            @Value("${app.canal.username:canal}") String user,
            @Value("${app.canal.password:canal123456}") String password,
            @Value("${app.canal.client-id:1001}") String clientId,
            @Value("${app.canal.filter:ecommerce\\.(product|product_image)}") String filter,
            @Value("${app.canal.batch-size:100}") int batchSize,
            @Value("${app.canal.timeout-ms:1000}") int timeoutMs) {
        this.db = db;
        this.searchIndex = searchIndex;
        this.es =
                builder.baseUrl(esUrl)
                        .defaultHeaders(
                                h -> {
                                    if (esUser != null && !esUser.isBlank())
                                        h.setBasicAuth(
                                                esUser, esPassword == null ? "" : esPassword);
                                })
                        .build();
        this.index = index;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.destination = destination;
        this.user = user;
        this.password = password;
        this.clientId = clientId;
        this.filter = filter;
        this.batchSize = batchSize;
        this.timeoutMs = timeoutMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        if (!clientId.matches("\\d+") || Integer.parseInt(clientId) > Short.MAX_VALUE)
            throw new IllegalStateException("Canal client-id must be numeric and fit signed short");
        thread = new Thread(this::run, "canal-product-cdc");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void shutdown() {
        stop.set(true);
        Socket s = socket.getAndSet(null);
        if (s != null)
            try {
                s.close();
            } catch (Exception ignored) {
            }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("connected", connected);
        m.put("host", host);
        m.put("port", port);
        m.put("destination", destination);
        m.put("client_id", clientId);
        m.put("batches", batches.get());
        m.put("insert", inserted.get());
        m.put("update", updated.get());
        m.put("delete", deleted.get());
        m.put("image", images.get());
        m.put("skipped", skipped.get());
        m.put("last_event", lastEvent.get());
        m.put("last_success", lastSuccess == null ? null : lastSuccess.toString());
        m.put("last_error", error.get());
        return m;
    }

    private void run() {
        long pause = 5000;
        while (!stop.get()) {
            try {
                searchIndex.ensureIndex();
                try (Socket s = new Socket()) {
                    s.connect(new java.net.InetSocketAddress(host, port), 10000);
                    socket.set(s);
                    s.setSoTimeout(30000);
                    CanalProtocol.Packet hello = CanalProtocol.read(s);
                    if (hello.type() != CanalProtocol.HANDSHAKE)
                        throw new java.io.IOException(
                                "Expected Canal handshake, received " + hello.type());
                    Map<Integer, List<Object>> hf = CanalProtocol.parse(hello.body());
                    byte[] seed = CanalProtocol.raw(hf, 2);
                    CanalProtocol.send(
                            s,
                            CanalProtocol.AUTH,
                            CanalProtocol.auth(
                                    user, password, seed, destination, clientId, filter));
                    CanalProtocol.expectAck(s, "authentication");
                    byte[] sub = CanalProtocol.destClient(destination, clientId);
                    sub = concat(sub, CanalProtocol.str(7, filter));
                    CanalProtocol.send(s, CanalProtocol.SUB, sub);
                    CanalProtocol.expectAck(s, "subscription");
                    connected = true;
                    error.set(null);
                    pause = 5000;
                    LOG.info("Canal subscribed: " + destination + " filter=" + filter);
                    while (!stop.get()) {
                        CanalProtocol.Batch batch =
                                CanalProtocol.get(s, destination, clientId, batchSize, timeoutMs);
                        if (batch.id() < 0 || batch.entries().isEmpty()) continue;
                        boolean ok = false;
                        try {
                            process(CanalProtocol.entries(batch.entries()));
                            ok = true;
                        } finally {
                            CanalProtocol.send(
                                    s,
                                    ok ? CanalProtocol.CLIENT_ACK : CanalProtocol.ROLLBACK,
                                    CanalProtocol.batchId(destination, clientId, batch.id()));
                        }
                        if (ok) {
                            batches.incrementAndGet();
                            lastSuccess = Instant.now();
                        }
                    }
                }
            } catch (Exception ex) {
                connected = false;
                error.set(
                        ex.getClass().getSimpleName()
                                + ": "
                                + Objects.toString(ex.getMessage(), ""));
                if (!stop.get())
                    LOG.log(Level.WARNING, "Canal connection/processing failed; reconnecting", ex);
                Socket s = socket.getAndSet(null);
                if (s != null)
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    if (stop.get()) break;
                    Thread.currentThread().interrupt();
                }
                pause = Math.min(60000, pause * 2);
            } finally {
                connected = false;
                socket.set(null);
            }
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private void process(List<CanalProtocol.Entry> events) throws Exception {
        for (CanalProtocol.Entry ev : events) {
            lastEvent.set(ev.schema() + "." + ev.table() + "/" + ev.type());
            if (ev.ddl() || !"ecommerce".equals(ev.schema())) {
                skipped.incrementAndGet();
                continue;
            }
            if ("product".equals(ev.table())) {
                for (CanalProtocol.ColumnChange row : ev.rows()) processProduct(ev.type(), row);
            } else if ("product_image".equals(ev.table())) {
                for (CanalProtocol.ColumnChange row : ev.rows()) processImage(ev.type(), row);
            } else skipped.incrementAndGet();
        }
    }

    private void processProduct(int type, CanalProtocol.ColumnChange row) throws Exception {
        Map<String, String> data = type == 3 ? row.before() : row.after();
        String esin = data.get("esin");
        if (esin == null || esin.isBlank()) {
            skipped.incrementAndGet();
            return;
        }
        if (type == 3) {
            delete(esin);
            deleted.incrementAndGet();
            return;
        }
        if (type != 1 && type != 2) {
            skipped.incrementAndGet();
            return;
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        for (String field : List.of("id", "user_id")) {
            if (data.containsKey(field)) doc.put(field, number(data.get(field)));
        }
        for (String field : List.of("esin", "category", "country", "source_file")) {
            if (data.containsKey(field)) doc.put(field, data.get(field));
        }
        if (data.containsKey("price")) doc.put("price", decimal(data.get("price")));
        if (data.containsKey("create_time")) doc.put("create_time", data.get("create_time"));
        for (String lang : LANGS) {
            for (String suffix : List.of("_title", "_desc")) {
                String field = lang + suffix;
                if (data.containsKey(field)) doc.put(field, data.get(field));
            }
        }
        if (data.containsKey("zh_title") || data.containsKey("en_title")) {
            Map<String, Object> current =
                    db.queryForMap("select zh_title,en_title from product where esin=?", esin);
            List<String> inputs = new ArrayList<>();
            for (String f : List.of("zh_title", "en_title")) {
                Object v = current.get(f);
                if (v != null && !v.toString().isBlank())
                    inputs.add(v.toString().substring(0, Math.min(80, v.toString().length())));
            }
            inputs.add(esin);
            doc.put("suggest", Map.of("input", inputs));
        }
        if (doc.isEmpty()) {
            skipped.incrementAndGet();
            return;
        }
        update(esin, doc, true);
        if (type == 1) inserted.incrementAndGet();
        else updated.incrementAndGet();
    }

    private void processImage(int type, CanalProtocol.ColumnChange row) throws Exception {
        Map<String, String> src = type == 3 ? row.before() : row.after();
        String pid = src.get("product_id");
        if (pid == null) {
            skipped.incrementAndGet();
            return;
        }
        List<Map<String, Object>> rows =
                db.queryForList(
                        "select p.esin,(select pi.image_url from product_image pi where"
                            + " pi.product_id=p.id order by pi.sort,pi.id limit 1) primary_image"
                            + " from product p where p.id=?",
                        Long.parseLong(pid));
        if (rows.isEmpty()) {
            skipped.incrementAndGet();
            return;
        }
        Map<String, Object> r = rows.get(0);
        String esin = Objects.toString(r.get("esin"), "");
        if (esin.isBlank()) {
            skipped.incrementAndGet();
            return;
        }
        try {
            Map<String, Object> imageDoc = new LinkedHashMap<>();
            imageDoc.put("image_url", r.get("primary_image"));
            update(esin, imageDoc, false);
            images.incrementAndGet();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                skipped.incrementAndGet();
                return;
            }
            throw e;
        }
    }

    private void update(String esin, Map<String, Object> doc, boolean upsert) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("doc", doc);
        if (upsert) body.put("doc_as_upsert", true);
        es.post()
                .uri("/{index}/_update/{id}", index, esin)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void delete(String esin) {
        try {
            es.delete().uri("/{index}/_doc/{id}", index, esin).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) throw e;
        }
    }

    private static Object number(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static Object decimal(String s) {
        if (s == null) return null;
        try {
            return new java.math.BigDecimal(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }
}
