import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

public class CrptApi {
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final int requestLimit;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be a positive number.");
        }

        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.semaphore = new Semaphore(requestLimit, true);

        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, 0, 1, timeUnit);
    }

    public void createDocument(String jsonDocument, String signature) throws Exception {
        semaphore.acquire();
        try {
            URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Signature", signature);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonDocument.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : " + responseCode);
            }

        } finally {
            semaphore.release();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}

class CrptApiTest {
    public static void main(String[] args) {
        try {
            CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

            String jsonDocument = "{ \"description\": { \"participantInn\": \"1234567890\" }," +
                    " \"doc_id\": \"doc123\", " +
                    "\"doc_status\": \"NEW\"," +
                    " \"doc_type\": \"LP_INTRODUCE_GOODS\"," +
                    " \"importRequest\": true, " +
                    "\"owner_inn\": \"1234567890\", " +
                    "\"participant_inn\": \"1234567890\"," +
                    " \"producer_inn\": \"1234567890\"," +
                    "\"production_date\": \"2020-01-23\", " +
                    "\"production_type\": \"PRODUCED\"," +
                    " \"products\": [{ \"certificate_document\": \"cert123\", \"certificate_document_date\": \"2020-01-23\", \"certificate_document_number\": \"12345\", \"owner_inn\": \"1234567890\", \"producer_inn\": \"1234567890\", \"production_date\": \"2020-01-23\", \"tnved_code\": \"1234567890\", \"uit_code\": \"code123\", \"uitu_code\": \"code456\" }]," +
                    " \"reg_date\": \"2020-01-23\", \"reg_number\": \"reg123\" }";
            String signature = "example-signature";

            api.createDocument(jsonDocument, signature);

            api.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

