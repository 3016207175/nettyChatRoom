package cn.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class LoginRequestMessage extends Message {
    private String username;
    private String password;

    @Override
    public int getMessageType() {
        return LoginRequestMessage;
    }
}
