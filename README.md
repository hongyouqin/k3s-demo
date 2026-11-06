# Spring Boot + k3s 部署学习示例

一个可以直接构建、部署到 k3s 的最小但完整的 Spring Boot 应用，用来练习 Kubernetes 的核心概念。

## 这个示例包含什么

- 两个副本的 `Deployment`，演示滚动更新与自我修复
- `Service` 把流量负载均衡到多个 Pod
- `Ingress` 使用 k3s 自带的 Traefik 暴露 HTTP 服务
- `ConfigMap` 注入应用配置
- `startupProbe`、`readinessProbe`、`livenessProbe` 三个探针
- CPU / 内存的 requests 与 limits
- `HorizontalPodAutoscaler`（HPA）基于 CPU 自动扩缩容
- 非 root 运行、只读根文件系统、丢弃 Linux capabilities 等安全配置
- 优雅停机
- Downward API 把 Pod 名称、IP、命名空间、节点名传给应用

请求路径：

```text
客户端
  │
  ▼
Traefik Ingress (k3s-demo.local)
  │
  ▼
Service k3s-demo:80
  │
  ├──────────────┐
  ▼              ▼
Pod 1:8080     Pod 2:8080
  │
  └── /actuator/health/{liveness,readiness} 供 kubelet 探活
      /api/cpu 用于制造 CPU 负载，观察 HPA
```

## 目录结构

```text
.
├── pom.xml
├── Dockerfile
├── Makefile
├── src
│   ├── main
│   │   ├── java/com/example/k3sdemo
│   │   │   ├── K3sDemoApplication.java
│   │   │   ├── api
│   │   │   │   ├── CpuResult.java
│   │   │   │   ├── DemoController.java
│   │   │   │   ├── HelloResponse.java
│   │   │   │   └── PodInfo.java
│   │   │   └── config/AppProperties.java
│   │   └── resources/application.yml
│   └── test/java/com/example/k3sdemo/K3sDemoApplicationTests.java
└── k8s
    ├── kustomization.yaml
    ├── namespace.yaml
    ├── configmap.yaml
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── hpa.yaml
    └── examples/load-generator.yaml
```

## 环境要求

- JDK 17+
- Maven 3.6.3+
- Docker（或 k3s 节点上可用的 nerdctl/buildah）
- 一个可用的 k3s 集群
- `kubectl`，并且已经能访问该集群
- 可选的 `jq`，用于格式化 JSON 输出

> k3s 运行在 Linux 上。macOS 开发机可以使用远程 Linux 服务器、Multipass/Lima 创建的 Linux 虚拟机，或 k3d。下面的步骤默认你已经有可用的 k3s 集群和 kubeconfig。

先确认集群正常：

```bash
kubectl get nodes
kubectl get pods -A
```

## 1. 本地运行与测试

```bash
# 运行测试
mvn -B test

# 启动应用
mvn spring-boot:run
```

另开终端：

```bash
curl -s http://localhost:8080/api/info
curl -s "http://localhost:8080/api/hello?name=K3s"
curl -s "http://localhost:8080/api/cpu?millis=100"
curl -s http://localhost:8080/actuator/health
```

本地运行时，Pod 相关字段会是 `local` 或开发机主机名。

## 2. 构建镜像

### 确认 k3s 节点架构

在 k3s 节点上执行：

```bash
uname -m
# x86_64  -> linux/amd64
# aarch64 -> linux/arm64
```

镜像架构必须与 k3s 节点一致。下面是 x86_64 的示例：

```bash
docker build --platform linux/amd64 -t k3s-demo:1.0.0 .
```

或者使用 Makefile：

```bash
make image PLATFORM=linux/amd64

# ARM64 节点使用：
# make image PLATFORM=linux/arm64
```

## 3. 让 k3s 能获取镜像

k3s 默认使用 containerd，而不是 Docker。即使本机 `docker images` 能看到镜像，k3s 也看不到，需要选择下面一种方式之一。

### 方式 A：k3s 与 Docker 在同一台机器（最简单）

```bash
docker save k3s-demo:1.0.0 | sudo k3s ctr images import -
sudo k3s ctr images ls | grep k3s-demo
```

也可以使用：

```bash
make image-import
```

多节点 k3s 集群需要在**每个可能调度该 Pod 的节点**上导入镜像，或者改用镜像仓库。

### 方式 B：使用镜像仓库

```bash
docker tag k3s-demo:1.0.0 registry.example.com/demo/k3s-demo:1.0.0
docker push registry.example.com/demo/k3s-demo:1.0.0
```

然后修改 `k8s/kustomization.yaml` 中的 `images`：

```yaml
images:
  - name: k3s-demo
    newName: registry.example.com/demo/k3s-demo
    newTag: 1.0.0
```

私有仓库还需要在命名空间中创建 `imagePullSecret`，并在 Deployment 中引用。

### 方式 C：使用 k3d

如果本地集群是用 k3d 创建的：

```bash
k3d image import k3s-demo:1.0.0 -c <你的集群名称>
```

## 4. 部署到 k3s

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -k k8s
kubectl rollout status deployment/k3s-demo -n k3s-demo --timeout=180s
```

或：

```bash
make deploy
```

查看资源：

```bash
kubectl get deployment,replicaset,pod,service,ingress,hpa -n k3s-demo -o wide
# 或
make status
```

期望看到 2 个 Pod 处于 `Running` 且 `READY 1/1`。如果看到 `ImagePullBackOff`，回到第 3 步检查镜像是否已导入，以及架构是否匹配。

## 5. 访问应用

### 通过端口转发（最可靠）

```bash
kubectl port-forward -n k3s-demo service/k3s-demo 8080:80
# 或 make forward
```

另一个终端：

```bash
curl -s http://localhost:8080/api/info
curl -s "http://localhost:8080/api/hello?name=K3s"
curl -s http://localhost:8080/actuator/health
```

后续实验中的 `localhost:8080` 请求都假设端口转发仍在运行。

连续请求几次，观察 `podName` 在多个副本之间变化：

```bash
for i in $(seq 1 8); do
  curl -s "http://localhost:8080/api/hello?name=K3s"
  echo
done
```

### 通过 Ingress

先找到 k3s 节点 IP 和 Traefik 服务：

```bash
kubectl get nodes -o wide
kubectl get service -n kube-system traefik
```

把节点 IP 写入本机 hosts（示例）：

```bash
echo "<NODE_IP> k3s-demo.local" | sudo tee -a /etc/hosts
curl -s http://k3s-demo.local/api/info
```

或者不改 hosts，直接指定 Host 头：

```bash
curl -s -H "Host: k3s-demo.local" http://<NODE_IP>/api/info
```

Ingress 相关排查：

```bash
kubectl get ingress -n k3s-demo
kubectl describe ingress -n k3s-demo k3s-demo
kubectl get pods -n kube-system -l app.kubernetes.io/name=traefik
```

## 6. 学习实验

### 实验一：滚动更新与回滚

本示例的版本通过 ConfigMap 注入。修改 ConfigMap 并重启 Deployment：

```bash
kubectl -n k3s-demo patch configmap k3s-demo-config \
  --type merge \
  -p '{"data":{"GREETING":"Hello from ConfigMap v2"}}'

kubectl -n k3s-demo rollout restart deployment/k3s-demo
kubectl -n k3s-demo rollout status deployment/k3s-demo

curl -s "http://localhost:8080/api/hello?name=K3s"
```

观察滚动更新过程中 Pod 逐个替换：

```bash
kubectl get pods -n k3s-demo -w
```

回滚：

```bash
kubectl -n k3s-demo rollout history deployment/k3s-demo
kubectl -n k3s-demo rollout undo deployment/k3s-demo
kubectl -n k3s-demo rollout status deployment/k3s-demo
```

恢复 ConfigMap 原始内容：

```bash
kubectl apply -f k8s/configmap.yaml
kubectl -n k3s-demo rollout restart deployment/k3s-demo
```

> `envFrom` 注入的环境变量只在容器启动时读取，修改 ConfigMap 不会热更新到运行中的 JVM。因此需要 rollout restart。生产环境通常会加配置校验和（checksum）注解，让配置变更自动触发滚动更新。

### 实验二：自我修复

先开一个终端持续观察：

```bash
kubectl get pods -n k3s-demo -w
```

另开终端删除 Pod：

```bash
kubectl delete pod -n k3s-demo -l app.kubernetes.io/name=k3s-demo
```

ReplicaSet 会重新创建 Pod，使数量恢复到 2 个。

### 实验三：HPA 自动扩缩容

k3s 默认通常会部署 metrics-server，先确认指标可用：

```bash
kubectl get apiservice v1beta1.metrics.k8s.io
kubectl top pods -n k3s-demo
kubectl get hpa -n k3s-demo
```

如果 `kubectl top` 报错或 HPA 的 TARGETS 显示 `<unknown>`，需要先修好 metrics-server。

启动负载（3 个并发客户端持续请求 CPU 消耗接口）：

```bash
kubectl apply -f k8s/examples/load-generator.yaml
```

观察 HPA 扩容：

```bash
kubectl get hpa -n k3s-demo -w
# 另一个终端
kubectl get pods -n k3s-demo -w
```

HPA 按 CPU **request**（100m）的 50% 作为目标。负载持续一段时间后，副本数会从 2 增加到 `maxReplicas: 4`。停止负载并等待缩容窗口：

```bash
kubectl delete -f k8s/examples/load-generator.yaml
kubectl get hpa -n k3s-demo -w
```

清理：

```bash
kubectl delete -f k8s/examples/load-generator.yaml --ignore-not-found
```

### 实验四：观察探针与优雅停机

```bash
kubectl describe pod -n k3s-demo -l app.kubernetes.io/name=k3s-demo
kubectl logs -n k3s-demo deployment/k3s-demo --tail=100
```

- `startupProbe`：给 JVM 启动留出时间，成功前不会执行 liveness/readiness 探针
- `readinessProbe` 失败：Pod 被从 Service Endpoints 摘除，不再接收流量
- `livenessProbe` 失败：kubelet 重启容器
- Pod 删除时，应用有最多 `terminationGracePeriodSeconds: 30` 秒完成优雅停机

### 实验五：查看探针端点

```bash
kubectl exec -n k3s-demo deployment/k3s-demo -- \
  wget -qO- http://localhost:8080/actuator/health/readiness
```

## 常用排查命令

```bash
# Pod 状态和重启原因
kubectl get pods -n k3s-demo -o wide
kubectl describe pod -n k3s-demo -l app.kubernetes.io/name=k3s-demo

# 应用日志
kubectl logs -n k3s-demo deployment/k3s-demo --tail=200
kubectl logs -n k3s-demo deployment/k3s-demo --previous

# 容器内的环境变量（应能看到 POD_NAME、POD_NAMESPACE 等）
kubectl exec -n k3s-demo deployment/k3s-demo -- env | sort

# Service 是否有正确的 Endpoints
kubectl get endpoints -n k3s-demo k3s-demo

# 集群事件
kubectl get events -n k3s-demo --sort-by=.lastTimestamp

# 从集群内访问 Service
kubectl run curl-test -n k3s-demo --rm -it --restart=Never \
  --image=curlimages/curl:8.12.1 -- \
  curl -s http://k3s-demo/api/info
```

常见问题：

| 现象 | 排查方向 |
| --- | --- |
| `ImagePullBackOff` / `ErrImageNeverPull` | 镜像没导入 k3s containerd、仓库不可访问，或 CPU 架构不匹配 |
| `CrashLoopBackOff` | `kubectl logs --previous`，重点看内存不足、配置绑定失败 |
| Pod 一直 `Running` 但 `READY 0/1` | readiness 探针失败；检查 `/actuator/health/readiness` 和启动时间 |
| Ingress 404 | Traefik 是否运行、`ingressClassName` 是否正确、Host 是否匹配 |
| HPA 显示 `<unknown>` | metrics-server 不可用或 Pod 没有设置 CPU requests |
| 修改 ConfigMap 没生效 | 环境变量只在启动时注入，需要 rollout restart |

## 清理

```bash
kubectl delete -f k8s/examples/load-generator.yaml --ignore-not-found
kubectl delete -k k8s --ignore-not-found
```

或者：

```bash
make undeploy
```

## 生产环境提醒

这个项目为教学而设计，直接用于生产前至少需要：

- 删除或保护 `/api/cpu` 这个故意消耗 CPU 的接口
- 关闭 Actuator 的 `show-details: always`，只暴露必要端点
- 通过 Secret 管理敏感配置，而不是 ConfigMap
- 为 Ingress 配置 TLS
- 使用固定/不可变镜像标签，并用镜像仓库的拉取密钥
- 根据实际 JVM 内存设置调整 limits（可用 `-XX:MaxRAMPercentage`）
