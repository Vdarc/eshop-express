ARG ES_VERSION=8.13.4
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

# 安装中文分词插件 analysis-ik
# IK 版本必须与 ES 版本严格一致，否则插件加载失败导致节点无法启动
ARG ES_VERSION
ARG IK_URL=https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v${ES_VERSION}/elasticsearch-analysis-ik-${ES_VERSION}.zip

# 国内网络拉取较慢时，可改用镜像源（取消下面一行注释并注释掉上面那行）
# ARG IK_URL=https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}

RUN bin/elasticsearch-plugin install --batch ${IK_URL}

# 单节点开发环境：关闭安全认证以便本地直连 9200
# 生产环境务必开启 xpack.security 并配置账号密码（见 docker-compose.yml 的 ES_USERNAME/ES_PASSWORD）
