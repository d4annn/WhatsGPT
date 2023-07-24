public record CompletionRequestF(String model, String prompt,
                                 double temperature, int max_tokens) {

    public static CompletionRequestF defaultWith(String prompt) {
        return new CompletionRequestF("text-davinci-003", prompt, 0.7, 1000);
    }

}

