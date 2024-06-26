docker buildx build --platform linux/aarch64 --tag mariokorte/huaweiapi:latest ./Docker
docker save -o ~/Downloads/huaweiapi.tar mariokorte/huaweiapi:latest