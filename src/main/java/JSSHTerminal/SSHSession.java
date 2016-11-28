package JSSHTerminal;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import javax.swing.*;
import java.io.*;

/**
 * SSH session
 */
public final class SSHSession implements UserInfo {

  private final static int NAGLE_PERIOD = 50; // Nagle period in ms

  private TerminalEmulator terminal = null;
  private JSchSession jschsession = null;
  private JTextField passwordField = new JPasswordField(20);
  private String _password = null;
  private MainPanel _parent;

  private Reader in = null;
  private Writer out = null;
  private ChannelShell channel = null;
  private Thread pumpThread;
  private Thread nagleThread;
  final private char[] inBuffer = new char[65536];
  private int inBufferPos = 0;
  private boolean answerYes = false;
  private boolean x11forwarding = false;
  private int retryCount;

  /**
   * @param parent Parent terminal frame
   * @param width  Width of the terminal. For example, 80.
   * @param height Height of the terminal. For example, 25.
   * @param scrollSize Scrollbar buffer size
   */
  public SSHSession(MainPanel parent, int width, int height, int scrollSize)  {

    _parent = parent;
    terminal = new TerminalEmulator(width, height,scrollSize);
    terminal.reset();
    _parent.repaint();
    retryCount = 0;

    String defaultSSHDir = System.getProperty("user.home") + "/.ssh";
    JSch jSch = JSchSession.getJSch();
    try {
      jSch.setKnownHosts(defaultSSHDir+"/known_hosts");
    } catch (JSchException e) {
      System.out.println("Warning, jSch.setKnownHosts() failed " + e.getMessage());
    }

  }

  /**
   * Automatically answer yes to question
   * @param enable true to enable, false otherwise
   */
  public void setAnswerYes(boolean enable) {
    answerYes = enable;
  }

  /**
   * Enable X11 forwarding
   * @param enable true to enable, false otherwise
   */
  public void setX11Forwarding(boolean enable) {
    x11forwarding = enable;
  }

  /**
   * Connect to a host using name and password.
   * @param host Host to connect
   * @param user username
   * @param password password (if null, password will be prompted)
   * @throws IOException
   */
  public void connect(String host, String user, String password) throws IOException {

    _password = password;
    try {
      jschsession = JSchSession.getSession(user, null, host, 22, this, null);
      channel = (ChannelShell)jschsession.getSession().openChannel("shell");
      if(x11forwarding) {
          jschsession.getSession().setX11Host("127.0.0.1");
          jschsession.getSession().setX11Port(6000);
          channel.setXForwarding(true);
      }
      out = new OutputStreamWriter(channel.getOutputStream());
      in = new InputStreamReader(channel.getInputStream());
      channel.setPtyType("xterm");
      channel.connect();
      //write("ssh -X dserver@l-srrf-8");
      //write(TerminalEmulator.getCodeENTER());

    } catch (JSchException e) {
      throw new IOException(e.getMessage());
    }

    pumpThread = new Thread(new Runnable() {
      @Override
      public void run() {
        pump();
      }
    });
    pumpThread.start();

    nagleThread = new Thread(new Runnable() {
      @Override
      public void run() {
        naggle();
      }
    });
    nagleThread.start();

  }

  public boolean isConnected() {
    return jschsession!=null;
  }

  public void close() {

    if(jschsession!=null) {
      jschsession.dispose();

      try {
        pumpThread.join();
        nagleThread.join();
      } catch (InterruptedException e) {}
      jschsession = null;
    }

  }


  void write(byte[] buff) throws IOException {

    int len = buff.length;
    char[] cout = new char[len];
    for(int i=0;i<len;i++) cout[i]=(char)buff[i];
    out.write(cout,0,len);
    out.flush();

  }

  public void write(String k) {

    if (k != null && k.length() != 0) {
      try {
        out.write(k);
        out.flush();
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
    }

  }

  public TerminalEmulator getTerminal() {

    return terminal;

  }

  void resize(int width,int height) {

    if(channel!=null)
      channel.setPtySize(width,height, width*8,height*16);

  }

  private void naggle() {

    while(isConnected()) {

      synchronized (inBuffer) {
        if( inBufferPos>0 ) {
          terminal.write(inBuffer,inBufferPos);
          inBufferPos = 0;
          write(terminal.read());
          _parent.repaint();
          _parent.updateScrollBar();
        }
      }
      try {
        Thread.sleep(NAGLE_PERIOD);
      } catch (InterruptedException e) {}

    }

  }

  private void pump() {

    char[] buf = new char[1024];
    int len;

    try {
      try {
        while ((len = in.read(buf)) >= 0) {
          synchronized (inBuffer) {
            if(inBufferPos+len > inBuffer.length) {
              terminal.write(inBuffer,inBufferPos);
              terminal.write(buf,len);
              inBufferPos = 0;
              _parent.updateScrollBar();
              _parent.repaint();
            } else {
              System.arraycopy(buf,0,inBuffer,inBufferPos,len);
              inBufferPos+=len;
            }
          }
        }
      } catch (IOException e) {
        System.out.println(e.getMessage());
      } finally {
        closeQuietly(in);
        closeQuietly(out);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    jschsession = null;
    _parent.exitFrame();

  }


  private void closeQuietly(Closeable c) {

    try {
      if (c != null) c.close();
    } catch (IOException e) {
      // silently ignore
    }

  }

  @Override
  public String getPassphrase() {
    return null;
  }

  @Override
  public String getPassword() {
    retryCount++;
    return _password;
  }

  @Override
  public boolean promptPassword(String message) {

    if(_password!=null && retryCount==1) return true;

    JPanel panel = new JPanel();
    panel.add(passwordField);
    passwordField.requestFocusInWindow();
    JOptionPane pane = new JOptionPane(panel,
        JOptionPane.QUESTION_MESSAGE,
        JOptionPane.OK_CANCEL_OPTION) {
      public void selectInitialValue() {
      }
    };

    JDialog dialog = pane.createDialog(_parent,
        message);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setVisible(true);
    Object o = pane.getValue();

    if (o != null && ((Integer) o).intValue() == JOptionPane.OK_OPTION) {
      _password = passwordField.getText();
      return true;
    } else {
      return false;
    }

  }

  @Override
  public boolean promptPassphrase(String s) {
    return true;
  }

  @Override
  public boolean promptYesNo(String str) {

    if(answerYes)
        return true;

    Object[] options = {"Yes", "No"};
    int ok = JOptionPane.showOptionDialog(_parent, str,
        "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
        null, options, options[0]);
    return ok == 0;

  }

  @Override
  public void showMessage(String s) {
    JOptionPane.showMessageDialog(_parent, s, "Message", JOptionPane.INFORMATION_MESSAGE);
  }

}
