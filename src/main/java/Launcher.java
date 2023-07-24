import com.google.gson.Gson;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.service.OpenAiService;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.message.standard.ImageMessage;
import it.auties.whatsapp.model.message.standard.TextMessage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Launcher {

    private static final URI CHATGPT_URI = URI.create("https://api.openai.com/v1/completions");
    private static HttpClient client = HttpClient.newHttpClient();
    private static Gson gson = new Gson();
    private static long time = 0;
    private static ExecutorService exe = Executors.newFixedThreadPool(1);
    private static Map<String, List<ChatMessage>> chats = new HashMap<>();
    private static String token = "token";
    private static OpenAiService service = new OpenAiService(token, Duration.ofMinutes(5));

    public static void main(String[] args) throws IOException, InterruptedException {
        Whatsapp.newConnection()
                .addLoggedInListener(() -> System.out.println("Connected"))
                .addNewMessageListener(Launcher::onMessage)
                .connect()
                .join();
    }

    public static void onMessage(Whatsapp api, MessageInfo info) {
        if (!(info.message()
                .content() instanceof TextMessage textMessage)) {
            return;

        }

        if (!textMessage.text().startsWith(".") && !textMessage.text().startsWith("!")) return;
        if (System.currentTimeMillis() > time + 1000)
            System.out.println("Creating " + textMessage.text());
        if (textMessage.text().startsWith(".")) {
            getMessage(textMessage.text(), info, api);
        } else getImage(textMessage.text(), info, api);
        time = System.currentTimeMillis();
    }

    public static void getImage(String prompt, MessageInfo info, Whatsapp api) {
        exe.execute(() -> {
            try {
                System.out.println(info.chatName());
                CreateImageRequest request = CreateImageRequest.builder()
                        .prompt(prompt)
                        .build();
                String url = service.createImage(request).getData().get(0).getUrl();
                BufferedImage bufferedImage = ImageIO.read(new URL(url));
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", stream);
                byte[] bytes = stream.toByteArray();
                var image = ImageMessage.simpleBuilder()
                        .media(bytes)
                        .caption(prompt)
                        .build();
                api.sendMessage(info.chatJid(), image);
            } catch (Exception e) {
                api.sendMessage(info.chatJid(), e.getMessage(), info);
            }
        });
    }


    public static void getMessage(String prompt, MessageInfo info, Whatsapp api) {
        prompt += ".En español";
        String finalPrompt = prompt;
        try {
            System.out.println(info.chatName());
            if (!chats.containsKey(info.chatName())) {
                chats.put(info.chatName(), new ArrayList<>());
            }
            List<ChatMessage> m = chats.get(info.chatName());
            m.add(new ChatMessage("user", finalPrompt));
            ChatCompletionRequest req = ChatCompletionRequest.builder().messages(m).model("gpt-3.5-turbo").build();
            try {
                ChatMessage reply = ((ChatCompletionChoice) service.createChatCompletion(req).getChoices().get(0)).getMessage();
                m.add(reply);
                if (m.size() > 10) m.remove(1);
                api.sendMessage(info.chatJid(), reply.getContent(), info);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /*

    public static void getMessage(String prompt, Whatsapp api, MessageInfo info) {
        try {
            var completion = CompletionRequest.defaultWith(prompt + ".En español");
            var postBodyJson = gson.toJson(completion);
            var responseBody = postToOpenAiApi(postBodyJson, OpenAiService.GPT_3);
            var completionResponse = gson.fromJson(responseBody, CompletionResponse.class);
            String s = completionResponse.firstAnswer().orElseThrow();
            System.out.println(s);
            api.sendMessage(info.chatJid(),s, info);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String postToOpenAiApi(String requestBodyAsJson, OpenAiService service)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder().uri(selectUri(service))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "en-US,en;q=0.9")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyAsJson)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static URI selectUri(OpenAiService service) {
        return URI.create(switch (service) {
            case DALL_E -> "https://api.openai.com/v1/images/generations";
            case GPT_3 -> "https://api.openai.com/v1/completions";
        });
    }

    public enum OpenAiService {
        DALL_E, GPT_3;
    }

     */
}
