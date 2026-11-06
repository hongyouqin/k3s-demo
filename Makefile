IMAGE ?= k3s-demo:1.0.0
PLATFORM ?= linux/amd64
NAMESPACE ?= k3s-demo

.DEFAULT_GOAL := help
.PHONY: help test build run image image-import deploy status logs forward undeploy

help: ## Show available targets
	@echo "Usage: make <target>"
	@echo ""
	@echo "  test          Run tests"
	@echo "  build         Build the executable jar"
	@echo "  run           Run locally on port 8080"
	@echo "  image         Build a container image (IMAGE=$(IMAGE), PLATFORM=$(PLATFORM))"
	@echo "  image-import  Import the image into a local k3s containerd"
	@echo "  deploy        Apply manifests and wait for the rollout"
	@echo "  status        Show workload, service, ingress and HPA status"
	@echo "  logs          Follow application logs"
	@echo "  forward       Port-forward the Service to localhost:8080"
	@echo "  undeploy      Delete all demo resources"
	@echo ""
	@echo "Examples:"
	@echo "  make image PLATFORM=linux/arm64"
	@echo "  make deploy"

test:
	mvn -B test

build:
	mvn -B clean package

run:
	mvn spring-boot:run

image:
	docker build --platform $(PLATFORM) -t $(IMAGE) .

image-import:
	docker save $(IMAGE) | sudo k3s ctr images import -

deploy:
	kubectl apply -f k8s/namespace.yaml
	kubectl apply -k k8s
	kubectl rollout status deployment/k3s-demo -n $(NAMESPACE) --timeout=180s

status:
	kubectl get deployment,replicaset,pod,service,ingress,hpa -n $(NAMESPACE) -o wide

logs:
	kubectl logs -n $(NAMESPACE) deployment/k3s-demo --tail=100 -f

forward:
	kubectl port-forward -n $(NAMESPACE) service/k3s-demo 8080:80

undeploy:
	kubectl delete -k k8s --ignore-not-found
