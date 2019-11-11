# spring-dapr-grpc-subscriber

The subscriber listens to the redis topic "MAIN" and saves all incoming messages to the dapr redis storage via saveState grpc call. After the saving it reads the value from the state and publishes a new redis topic "FINISHED", which propagetes the saved value. All service communication is implemented with grpc.

## howto make it work

  - install and initialize [Dapr](https://github.com/dapr/dapr)
  - mvn clean package
  - dapr run --protocol grpc --app-id subscriber --app-port 12302 -- java -jar target/dapr-subscribe.jar
  - make sure the [Publisher](https://github.com/devk-insurance/spring-dapr-grpc-publisher) is running
