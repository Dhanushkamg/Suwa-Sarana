package com.suwasarana.api.ai.poster;

import java.util.ArrayList;
import java.util.List;

/** Short, validated poster copy. All facts in the text come from the request's structured facts. */
public class PosterCopyResponseDto {
    private String headline;
    private String body;
    private List<String> hashtags = new ArrayList<>();
    private String caption;
    private boolean usedLlm;

    public PosterCopyResponseDto() {
    }

    public PosterCopyResponseDto(String headline, String body, List<String> hashtags, String caption, boolean usedLlm) {
        this.headline = headline;
        this.body = body;
        this.hashtags = hashtags;
        this.caption = caption;
        this.usedLlm = usedLlm;
    }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public List<String> getHashtags() { return hashtags; }
    public void setHashtags(List<String> hashtags) { this.hashtags = hashtags; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public boolean isUsedLlm() { return usedLlm; }
    public void setUsedLlm(boolean usedLlm) { this.usedLlm = usedLlm; }
}
