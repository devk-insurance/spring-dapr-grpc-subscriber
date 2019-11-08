# spring-dapr-grpc-subscriber

The subscriber listens to the redis stream "MAIN" and saves all incoming messages to the dapr redis storage via saveStage grpc call. After the saving it reads the value from the state and publishes a new redis stream "FINISHED", which is propagetes the saved value. All service communication is implemented with grpc.

## howto make it work

  - install and initialize Dapr
  - mvn clean package
  - dapr run --protocol grpc --app-id subscriber --app-port 12302 -- java -jar dapr-subscribe.jar
