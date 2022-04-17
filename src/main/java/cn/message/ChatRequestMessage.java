package cn.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class ChatRequestMessage extends Message {
    private String from;
    private String to;
    private String content;

    @Override
    public int getMessageType() {
        return ChatRequestMessage;
    }
}
