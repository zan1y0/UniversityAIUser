import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SMTP 发信自检（不经过 Spring / 不依赖任何第三方库）。
 *
 * 为什么不用 PowerShell：本机 Windows PowerShell 5.1 的 SslStream 走 Schannel，
 * 在这台机器上握手报 “No credentials are available in the security package”，
 * 而 Java 后端用的 JSSE 不受影响；用 Java 测才能复现后端真实行为。
 *
 * 用法（JDK 17 单文件直接运行，JDK 11+ 支持）：
 *   cd D:\STUDY\UniversityAIUser
 *   & "C:\Program Files\Java\jdk-17\bin\java.exe" scripts\SmtpProbe.java `
 *       -host smtp.qq.com -port 587 -user 1069168265@qq.com -to 1069168265@qq.com
 * 密码不写在命令行：省略 -pass 时会从控制台读取（不回显），避免进 PowerShell 历史记录。
 *
 * 参数：
 *   -host  SMTP 服务器           默认 smtp.qq.com
 *   -port  587(STARTTLS) / 465(隐式TLS)
 *   -user  登录账号（完整邮箱）   必填
 *   -pass  授权码/应用专用密码    省略则交互输入
 *   -from  发件人                 默认等于 -user（多数服务商要求两者一致）
 *   -to    收件人                 默认等于 -user
 */
public class SmtpProbe {
  private static BufferedReader in;
  private static OutputStream out;

  public static void main(String[] args) throws Exception {
    String host = "smtp.qq.com", user = null, pass = null, from = null, to = null;
    int port = 587;
    for (int i = 0; i < args.length - 1; i++) {
      String v = args[i + 1];
      switch (args[i]) {
        case "-host" -> host = v;
        case "-port" -> port = Integer.parseInt(v);
        case "-user" -> user = v;
        case "-pass" -> pass = v;
        case "-from" -> from = v;
        case "-to" -> to = v;
        default -> { }
      }
    }
    if (user == null) usage("缺少 -user");
    if (to == null) to = user;
    if (from == null) from = user;
    if (pass == null) {
      Console c = System.console();
      if (c == null) usage("没有可交互的控制台，请用 -pass 传入授权码");
      pass = new String(c.readPassword("请输入 %s 的 SMTP 授权码：", user));
    }
    boolean implicitTls = port == 465;

    System.out.println("java=" + System.getProperty("java.version") + " 目标=" + host + ":" + port
        + (implicitTls ? " 隐式TLS" : " STARTTLS"));

    try (Socket raw = new Socket()) {
      raw.connect(new java.net.InetSocketAddress(host, port), 10000);
      raw.setSoTimeout(15000);
      Socket socket = raw;
      if (implicitTls) {
        SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
            .createSocket(raw, host, port, true);
        tls.startHandshake();
        socket = tls;
        System.out.println("TLS 已建立：" + tls.getSession().getProtocol());
      }
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      out = socket.getOutputStream();

      expect("连接", read(), "220");
      String ehlo = line("EHLO smtp-probe.local");
      expect("EHLO", ehlo, "250");

      if (!implicitTls) {
        if (!ehlo.toUpperCase().contains("STARTTLS")) fail("服务器未通告 STARTTLS，若服务商只给 465，请用 -port 465");
        expect("STARTTLS", line("STARTTLS"), "220");
        SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
            .createSocket(socket, host, port, true);
        tls.startHandshake();
        in = new BufferedReader(new InputStreamReader(tls.getInputStream(), StandardCharsets.US_ASCII));
        out = tls.getOutputStream();
        System.out.println("TLS 已建立：" + tls.getSession().getProtocol());
        expect("EHLO(加密后)", line("EHLO smtp-probe.local"), "250");
      }

      String auth = line("AUTH LOGIN");
      expect("AUTH LOGIN", auth, "334");
      expect("用户名", line(b64(user)), "334");
      String authResult = line(b64(pass));
      if (!authResult.startsWith("235")) {
        fail("认证失败：" + authResult + "\n  常见原因：用了邮箱登录密码而不是授权码 / 未开启 SMTP 服务 / 授权码已失效");
      }
      System.out.println("认证通过");

      expect("MAIL FROM", line("MAIL FROM:<" + from + ">"), "250");
      String rcpt = line("RCPT TO:<" + to + ">");
      if (!rcpt.startsWith("250") && !rcpt.startsWith("251")) fail("收件人被拒绝：" + rcpt);
      expect("DATA", line("DATA"), "354");
      String body = "SMTP 自检邮件：收到说明 SMTP 参数可用。时间 " + java.time.LocalDateTime.now();
      String encoded = Base64.getMimeEncoder(76, "\r\n".getBytes())
          .encodeToString(body.getBytes(StandardCharsets.UTF_8));
      write("From: " + from + "\r\n"
          + "To: " + to + "\r\n"
          + "Subject: SMTP 自检 " + java.time.LocalTime.now().withNano(0) + "\r\n"
          + "MIME-Version: 1.0\r\n"
          + "Content-Type: text/plain; charset=UTF-8\r\n"
          + "Content-Transfer-Encoding: base64\r\n"
          + "\r\n"
          + encoded + "\r\n");
      expect("投递", line("."), "250");
      System.out.println("成功：邮件已投递给 " + host + "，请查收 " + to + " 的收件箱与垃圾邮件。");
      line("QUIT");
      socket.close();
    } catch (IOException e) {
      fail("网络异常：" + e);
    }
  }

  private static void usage(String reason) {
    System.err.println(reason + "\n用法：java scripts/SmtpProbe.java -host smtp.qq.com -port 587 -user 你@qq.com [-to 收件人] [-pass 授权码]");
    System.exit(2);
  }

  private static void fail(String message) {
    System.err.println("失败：" + message);
    System.exit(1);
  }

  private static String b64(String s) {
    return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private static void write(String text) throws IOException {
    out.write(text.getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  /** 发送一行命令，返回服务器完整应答（多行应答按空格拼接）。 */
  private static String line(String command) throws IOException {
    write(command + "\r\n");
    String reply = read();
    String head = command.length() > 40 ? command.substring(0, 12) + "...(已隐藏)" : command;
    System.out.println("  >> " + head);
    System.out.println("  << " + reply.replace("\r\n", " | "));
    return reply;
  }

  private static String read() throws IOException {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = in.readLine()) != null) {
      sb.append(line).append("\r\n");
      if (line.length() < 4 || line.charAt(3) != '-') break;
    }
    if (sb.length() == 0) throw new IOException("连接被对端关闭（可能被网络或安全软件阻断）");
    return sb.toString();
  }

  private static void expect(String stage, String reply, String code) {
    if (!reply.startsWith(code)) fail(stage + " 期望 " + code + "，实际：" + reply.trim());
  }
}
