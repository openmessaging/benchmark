# RUN IN KUBERNETES

## Build Docker container

```
./docker-build.sh
```

## Run local driver on Kubernetes:

```
kubectl run -n examples --rm -it --image pravega/openmessaging-benchmark:latest --serviceaccount examples-pravega openmessaging-benchmark
```

## Run in Kubernetes

```
./deploy-k8s-components.sh
```

