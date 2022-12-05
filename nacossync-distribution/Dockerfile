FROM hub.jdcloud.com/mesh/java8:centos7-amd64-secure-config-v20221104
#FROM arm64v8/openjdk:11 # for ARM64
MAINTAINER Zhiguo.Chen<chenzhiguo@jd.com>
USER root

ENV TZ Asia/Shanghai

WORKDIR /export/servers/
COPY ./target/*.zip /export/servers/
RUN /bin/bash -c 'for f in /export/servers/*; do if [ ${f##*.} == 'zip' ];then unzip -q -d /export/servers/ $f; fi; done'
RUN chmod +x /export/servers/nacos-sync/bin/*.sh
RUN rm -f *.zip

VOLUME ["/export/servers/nacos-sync/logs", "/export/servers/nacos-sync/conf"]
EXPOSE 8080
ENTRYPOINT ["/export/servers/nacos-sync/bin/startup.sh", "start", ""]
#ENTRYPOINT ["java","-jar","-Dloader.path=config","/export/servers/jmsf-provider.jar","--spring.config.location=/export/servers/config/"]
