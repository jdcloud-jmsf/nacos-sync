version=0.4.8-$(shell git rev-parse --short HEAD)

all-sync: clean build image-sync docker-login push-sync deploy-sync

clean:
	mvn clean -f ../pom.xml

build:
	mvn package -f ../pom.xml -DskipTests=true package

image-sync:
	docker build --platform linux/amd64 -t hub.jdcloud.com/mesh/nacos-sync:${version} ./

docker-login:
	docker login hub.jdcloud.com -u 'jrwangwei3' -p 'Jmsf1234'

push-sync:
	docker push hub.jdcloud.com/mesh/nacos-sync:${version}

deploy-sync:
	kubectl apply -f ./kubernetes/kubernetes-sync.yml