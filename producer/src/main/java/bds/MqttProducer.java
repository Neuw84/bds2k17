package bds;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.json.Json;
import lombok.Data;
import lombok.extern.java.Log;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * MQTT producer for Big Data Spain 2017
 *
 * @author Angel Conde
 */
@Log
@Data
public class MqttProducer implements Runnable {

    public static enum Type {
        NORMAL, CRITICAL, SUPERVISE;

    }

    private static final List<Type> TYPEVALUES = Collections.unmodifiableList(Arrays.asList(Type.values()));
    private static final int TYPESIZE = TYPEVALUES.size();

    private String broker;
    private String clientId;
    private int waitTime;

    public MqttProducer(String url, int waitTime, String clientId) {
        this.broker = url;
        this.clientId = clientId;
        this.waitTime = waitTime;
    }

    public static void main(String[] args) {
        if (args.length == 2) {
            int waitTime = 100;
            try {
                String broker = args[0];
                waitTime = Integer.parseInt(args[1]);
                MqttProducer m1 = new MqttProducer(broker, waitTime, "MACHINE1");
                MqttProducer m2 = new MqttProducer(broker, waitTime, "MACHINE2");
                new Thread(m1).start();
                new Thread(m2).start();
            } catch (NumberFormatException e) {
                System.out.println("Please introduce a valid number as second argument");
            }
        } else {
            System.out.println("Please introduce brokerURL (e.g. tcp://localhost:1883) and wait time in ms (200)");
        }
    }

    @Override
    public void run() {
        try {
            MqttClient client = new MqttClient(broker, clientId);
            client.connect();
            SplittableRandom random = new SplittableRandom(); //twice as fast as java.uti.random
            while (true) {
                MqttMessage message = new MqttMessage();
                byte[] payload = buildPayload(random);
                message.setPayload(payload);
                //simulate message duplicate each 30 messages 
                if (random.nextInt(30) == 9) {
                    client.publish("data", message);
                } else {
                    client.publish("data", message);
                    client.publish("data", message);
                }
                log.log(Level.FINE, "Message sent");
                TimeUnit.MILLISECONDS.sleep(waitTime);
            }

        } catch (MqttException | InterruptedException ex) {
            log.log(Level.SEVERE, null, ex);
        }

    }

    private byte[] buildPayload(SplittableRandom random) {
        Long miliseconds = 0L;
        //simulate unordering
        if (random.nextInt(10) == 9) {
            miliseconds = System.currentTimeMillis() - 1000L;
        } else {
            miliseconds = System.currentTimeMillis();
        }
        Timestamp ti = new Timestamp(miliseconds);
        String status = ((Type) TYPEVALUES.get(random.nextInt(TYPESIZE))).toString();
        double humidity = 0.0D;
        if (status.equals("CRITICAL")) {
            humidity = random.nextDouble(80.0D, 100.0D);
        } else {
            humidity = random.nextDouble(40.0D, 75.0D);
        }
        String json = Json.createObjectBuilder().add("machine", clientId).add("status", status).
                add("timestamp", ti.toString()).add("temperature", random.nextDouble(100.0D))
                .add("pressure", random.nextDouble(500.0D, 550.0D)).add("humidity", humidity).build().toString();
        return json.getBytes();

    }
}
