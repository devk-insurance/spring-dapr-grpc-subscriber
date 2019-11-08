package com.devk.grpcdemo.service;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.dapr.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;

import java.net.InetAddress;
import java.net.UnknownHostException;


@Slf4j
@GRpcService(interceptors = {LogInterceptor.class})
public class DaprClientServiceImpl extends DaprClientGrpc.DaprClientImplBase {


    private final String GRPC_PORT = System.getenv("DAPR_GRPC_PORT");
    private final String HOSTNAME = InetAddress.getLocalHost().getHostName();

    private static final String SAVE_TOPIC = "SAVE";
    private static final String FINISHED_TOPIC = "FINISHED";

    public DaprClientServiceImpl() throws UnknownHostException {
    }


    @Override
    public void getTopicSubscriptions(Empty request, StreamObserver<GetTopicSubscriptionsEnvelope> responseObserver) {
        responseObserver.onNext(GetTopicSubscriptionsEnvelope.newBuilder().addTopics(SAVE_TOPIC).build());
        responseObserver.onCompleted();
    }

    @Override
    public void onTopicEvent(CloudEventEnvelope request, StreamObserver<Empty> responseObserver) {

        final Any data = request.getData();
        Empty empty = null;


        try {
            final Any any = AnyUtils.unpackRedisEnvelope(data);
            if (any.is(KVRequest.class)) {
                final KVRequest unpack = any.unpack(KVRequest.class);
                final String key = unpack.getKey();
                final String value = unpack.getValue();
                log.info("Incoming KVRequest: key : {}, value : {}", key, value);
                empty = saveToRedisAndRetrieve(unpack);
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Bad Exception..", e);
        }

        responseObserver.onNext(empty);
        responseObserver.onCompleted();

    }

    private Empty saveToRedisAndRetrieve(KVRequest kvRequest) {
        // Saving in Redis
        Empty empty = null;
        ManagedChannel channel = null;
        try {

            channel = ManagedChannelBuilder.forAddress(HOSTNAME, Integer.parseInt(GRPC_PORT)).usePlaintext().build();
            DaprGrpc.DaprBlockingStub client = DaprGrpc.newBlockingStub(channel);
            ByteString bs = ByteString.copyFromUtf8(kvRequest.getValue());
            StateRequest newRequest = StateRequest.newBuilder().setKey(kvRequest.getKey()).
                    setValue(Any.newBuilder().setValue(bs)).build();

            SaveStateEnvelope newSSE = SaveStateEnvelope.newBuilder().addRequests(newRequest).build();
            empty = client.saveState(newSSE);

            GetStateEnvelope getSSE = GetStateEnvelope.newBuilder().setKey(kvRequest.getKey()).build();
            final GetStateResponseEnvelope state = client.getState(getSSE);
            final String loaded = state.getData().getValue().toStringUtf8();

            final KVRequest returning = KVRequest.newBuilder().setKey(kvRequest.getKey()).setValue(loaded).build();

            log.info("Loaded state: {} {}", returning.getKey(), returning.getValue());

            final ByteString bytes = Any.pack(returning).toByteString();
            final Any build = Any.newBuilder().setValue(bytes).build();

            final PublishEventEnvelope finished = PublishEventEnvelope.newBuilder().setTopic(FINISHED_TOPIC).
                    setData(build).build();

            client.publishEvent(finished);


        } catch (Exception ex) {
            log.error("Bad Exception..", ex);
        } finally {
            if (channel != null)
                channel.shutdown();
        }

        return empty;
    }

}

