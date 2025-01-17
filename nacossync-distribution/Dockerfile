ARG TPAAS_BUILD_IMAGE
ARG TPAAS_RUNTIME_IMAGE

FROM ${TPAAS_BUILD_IMAGE} as builder

MAINTAINER Zhiguo.Chen<chenzhiguo@jd.com>
USER root

WORKDIR /workspace

ENV TZ Asia/Shanghai
RUN wget --no-check-certificate http://d6.injdk.cn/oraclejdk/8/jdk-8u301-linux-x64.tar.gz  \
    && tar -zxvf jdk-8u301-linux-x64.tar.gz -C /usr/local \
    && mv /usr/local/jdk1.8.0_301/ /usr/local/jdk/

ENV JAVA_HOME /usr/local/jdk
ENV JRE_HOME /usr/local/jdk/jre
ENV PATH $JAVA_HOME/bin:$PATH

WORKDIR /export/servers/

COPY ./ /export/servers/

RUN mvn --batch-mode clean package -Dmaven.test.skip=true -Pbackend,dist-backend,repo-jd

FROM ${TPAAS_RUNTIME_IMAGE}
WORKDIR /export/servers/

COPY --from=builder /export/servers/nacossync-distribution/target/*.zip /export/servers/

COPY --from=builder /usr/local/* /usr/local/

ENV JAVA_HOME /usr/local/jdk
ENV JRE_HOME /usr/local/jdk/jre
ENV PATH $JAVA_HOME/bin:$PATH

RUN chmod -R 777 /export/servers/*
RUN yum -y install unzip

RUN /bin/bash -c 'for f in /export/servers/*; do if [ ${f##*.} == 'zip' ];then unzip -q -d /export/servers/ $f; fi; done'


RUN rm -f *.zip
RUN export JAVA_HOME
VOLUME ["/export/servers/nacos-sync/logs", "/export/servers/nacos-sync/conf"]
EXPOSE 8080
ENTRYPOINT ["/export/servers/nacos-sync/bin/startup.sh", "start", ""]
#ENTRYPOINT ["java","-jar","-Dloader.path=config","/export/servers/jmsf-provider.jar","--spring.config.location=/export/servers/config/"]
