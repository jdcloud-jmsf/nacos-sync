#version=0.4.11-$(shell git rev-parse --short HEAD)
version=0.4.11

all-sync: clean build image-sync docker-login push-sync deploy-sync

clean:
	mvn clean -f ../pom.xml

build:
	mvn package -f ../pom.xml -DskipTests=true package

image-sync:
	docker build --platform linux/amd64 -t hub.jdcloud.com/mesh/nacos-sync:${version} ./

push-sync:
	docker push hub.jdcloud.com/mesh/nacos-sync:${version}

deploy-sync:
	kubectl apply -f ./kubernetes/kubernetes-sync.yml