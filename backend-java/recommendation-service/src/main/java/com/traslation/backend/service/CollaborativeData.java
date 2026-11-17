package com.traslation.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CollaborativeData {
    private static final Pattern SHAPE =
            Pattern.compile("shape\\s*:\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
    private final List<SimulatedUser> users;
    private final float[][] ratings;

    public CollaborativeData(ObjectMapper mapper) {
        List<SimulatedUser> loadedUsers = List.of();
        float[][] loadedRatings = new float[0][0];
        try (InputStream in =
                new ClassPathResource("recommendation/simulated-users.json").getInputStream()) {
            List<Map<String, Object>> raw = mapper.readValue(in, new TypeReference<>() {});
            loadedUsers =
                    raw.stream()
                            .map(
                                    r ->
                                            new SimulatedUser(
                                                    ((Number) r.get("user_id")).intValue(),
                                                    Objects.toString(r.get("country"), ""),
                                                    ((List<?>) r.get("prefer_categories"))
                                                            .stream()
                                                                    .map(Objects::toString)
                                                                    .toList()))
                            .toList();
        } catch (Exception ignored) {
        }
        try (InputStream in =
                new ClassPathResource("recommendation/rating-matrix.npy").getInputStream()) {
            loadedRatings = readNpy(in);
        } catch (Exception ignored) {
        }
        users = loadedUsers;
        ratings = loadedRatings;
    }

    public List<SimulatedUser> users() {
        return users;
    }

    public float[][] ratings() {
        return ratings;
    }

    public record SimulatedUser(int userId, String country, List<String> preferCategories) {}

    private static float[][] readNpy(InputStream stream) throws Exception {
        byte[] file = stream.readAllBytes();
        if (file.length < 12
                || file[0] != (byte) 0x93
                || file[1] != 'N'
                || file[2] != 'U'
                || file[3] != 'M'
                || file[4] != 'P'
                || file[5] != 'Y')
            throw new IllegalArgumentException("Unsupported NumPy matrix header");
        int major = Byte.toUnsignedInt(file[6]);
        int headerLength;
        int headerStart;
        if (major == 1) {
            headerLength =
                    Short.toUnsignedInt(
                            ByteBuffer.wrap(file, 8, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
            headerStart = 10;
        } else {
            headerLength = ByteBuffer.wrap(file, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            headerStart = 12;
        }
        String header = new String(file, headerStart, headerLength, StandardCharsets.US_ASCII);
        if (!header.contains("'<f4'")
                && !header.contains("'descr': '<f4'")
                && !header.contains("\"descr\": \"<f4\"")) {
            if (!header.contains("<f4"))
                throw new IllegalArgumentException("Only float32 NumPy matrices are supported");
        }
        if (header.contains("True"))
            throw new IllegalArgumentException("Fortran ordered matrix is unsupported");
        Matcher matcher = SHAPE.matcher(header);
        if (!matcher.find()) throw new IllegalArgumentException("NumPy matrix shape is missing");
        int rows = Integer.parseInt(matcher.group(1)), columns = Integer.parseInt(matcher.group(2));
        int offset = headerStart + headerLength;
        if ((long) rows * columns * Float.BYTES != file.length - offset)
            throw new IllegalArgumentException("NumPy matrix data size is invalid");
        ByteBuffer data =
                ByteBuffer.wrap(file, offset, file.length - offset).order(ByteOrder.LITTLE_ENDIAN);
        float[][] matrix = new float[rows][columns];
        for (int row = 0; row < rows; row++)
            for (int column = 0; column < columns; column++) matrix[row][column] = data.getFloat();
        return matrix;
    }
}
