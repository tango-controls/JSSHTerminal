package JSSHTerminal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * SSH Terminal frame
 *
 * @author JL PONS
 */

public class MainPanel extends JFrame implements AdjustmentListener,MouseWheelListener {

  public static final String DEFAULT_VERSION = "-.-";
  public static final String VERSION = getVersion();
  final static boolean isWindows;

  private TerminalEvent textArea;
  private JScrollBar    scrollBar;
  private SSHSession    session;
  private String        _host;
  private String        _user;
  private String        _password;
  private boolean       exitOnClose = false;
  private boolean       scrollUpdate;
  private String        command = null;

  static {
    String OS_NAME = System.getProperty("os.name");
    String _OS_NAME = OS_NAME.toLowerCase();
    isWindows = _OS_NAME.startsWith("windows");
  }

  /**
   * Construct a SSH terminal frame
   * @param host Host to connect
   * @param user Username
   * @param password Password (if null, password will be prompted)
   * @param width Terminal width (character)
   * @param height Terminal height (character)
   * @param scrollSize ScrollBar height (lines)
   */
  public MainPanel(String host, String user, String password, int width, int height, int scrollSize) {

    _host = host;
    _user = user;
    _password = password;

    // Use a TextTerminal without antialiasaed font under X11
    if(!isWindows)
      textArea = new TextTerminal(this,width,height);
    else
      textArea = new GraphicTerminal(this,width,height);

    session = new SSHSession(this,width,height,scrollSize);
    textArea.setSession(session);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(textArea, BorderLayout.CENTER);
    getContentPane().setBackground(Color.BLACK);
    scrollBar = new JScrollBar();
    scrollBar.setMinimum(0);
    scrollBar.setMaximum(height);
    scrollBar.setValue(0);
    scrollBar.setVisibleAmount(height);
    scrollBar.addAdjustmentListener(this);
    scrollUpdate = false;
    getContentPane().add(scrollBar,BorderLayout.EAST);
    setTitle("JSSHTerminal " + VERSION + " " + user + "@" + host);

    addWindowListener(new WindowAdapter() {

      @Override
      public void windowOpened(WindowEvent e) {
        try {
          session.connect(_host, _user, _password);
          if(command!=null) session.execCommand(command);
          textArea.notifySizeChange();
        } catch (IOException ex) {
          JOptionPane.showMessageDialog(null, "Cannot connect :" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          setVisible(false);
          exitFrame();
        }
        super.windowOpened(e);
      }

      @Override
      public void windowClosing(WindowEvent e) {
        exitFrame();
      }

    });
    addMouseWheelListener(this);

    pack();
    setLocationRelativeTo(null);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

  }

  /**
   * Exit when terminal is closed or exit
   * @param exitOnClose
   */
  public void setExitOnClose(boolean exitOnClose) {
    this.exitOnClose = exitOnClose;
  }

  /**
   * Execute the given command after connection
   * @param cmd Command to be executed (Do not add \n at the end)
   */
  public void setCommand(String cmd) {
   command = cmd;
  }

  /**
   * Sets the SSH port
   * @param port Port number
   */
  public void setSSHPort(int port) {
    session.setSshPort(port);
  }

  /**
   * Automatically answer yes to question
   * @param enable Enalbe auto yes
   */
  public void setAnswerYes(boolean enable) {
    session.setAnswerYes(enable);
  }

  /**
   * Enable X11 forwarding, multi display not supported
   * @param enable
   */
  public void setX11Forwarding(boolean enable) {
    session.setX11Forwarding(enable);
  }

  void setExtraTilte(String tilte) {
    setTitle("JSSHTerminal " + VERSION + " " + tilte);
  }

  void exitFrame() {
    if(session!=null)
      session.close();
    textArea.dispose();
    if(exitOnClose) System.exit(0);
    else setVisible(false);
  }

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e) {
    if(!scrollUpdate) {
      int sbValue = e.getValue();
      textArea.setScrollPos(sbValue);
    }
  }

  void updateScrollBar() {
    scrollUpdate = true;
    int scrollPos = textArea.scrollPos;
    int scrollSize = textArea.terminal.getScrollSize();
    int height = textArea.termHeight;
    scrollBar.setValues(scrollSize - scrollPos - height, height, 0, scrollSize);
    scrollUpdate = false;
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    textArea.moveScroll(-3*e.getWheelRotation());
    updateScrollBar();
    e.consume();
  }

  private static String getVersion() {
    Package p = MainPanel.class.getPackage();

    //if version is set in MANIFEST.mf
    if(p.getImplementationVersion() != null) return p.getImplementationVersion();

    return DEFAULT_VERSION;
  }
  // ----------------------------------------------------------------------

  static void dumpCharSet(String set) {

    System.out.print("\u001B[m");
    System.out.print("\u001B[0m");
    System.out.println("---------- Set ------- " + set);
    System.out.print("\u001B"+set);

    char c = 32;
    for(int i=0;i<6;i++) {
      for(int j=0;j<16;j++) {
        if(c<127) System.out.print(c);
        c++;
      }
      System.out.println();
    }

    // Bold
    System.out.print("\u001B[1m");
    c = 32;
    for(int i=0;i<6;i++) {
      for(int j=0;j<16;j++) {
        if(c<127) System.out.print(c);
        c++;
      }
      System.out.println();
    }

    System.out.print("\u001B(A");

  }

  static void dumpCharSets() {

    dumpCharSet("(A");
    dumpCharSet(")A");
    dumpCharSet("(B");
    dumpCharSet(")B");
    dumpCharSet("(0");
    dumpCharSet(")0");
    dumpCharSet("(1");
    dumpCharSet(")1");
    dumpCharSet("(2");
    dumpCharSet(")2");

  }

  // ----------------------------------------------------------------------

  public static void printUsage() {

    System.out.println("Usage: jterminal username@host [-p password] [-P port] [-y] [-s WxHxS] [-X] [-c command]");
    System.out.println("       username@host username used to login on host");
    System.out.println("       -p password password used to login");
    System.out.println("       -P SSH port number (default is 22)");
    System.out.println("       -y Answer yes to question");
    System.out.println("       -s WxHxS terminal size WidthxHeightxScrollbar");
    System.out.println("       -X Enable X11 forwarding");
    System.out.println("       -c command Execute command after connection");
    System.exit(0);

  }

  public static void main(String[] args) {

    int W = 80;
    int H = 24;
    int S = 500;
    int P = 22;
    String password = null;
    boolean yes = false;
    boolean X11 = false;
    String command = null;

    if(args.length==0)
      printUsage();

    String[] uh = args[0].split("@");
    if(uh.length!=2)
      printUsage();

    int argc = 1;
    while( argc<args.length ) {

      if(args[argc].equals("-y")) {
        yes = true;
        argc++;
        continue;
      } if(args[argc].equals("-X")) {
        X11 = true;
        argc++;
        continue;
      } else if( args[argc].equals("-p") ) {
        if(argc+1<args.length)
          password = args[argc+1];
        else
          printUsage();
        argc+=2;
        continue;
      } else if( args[argc].equals("-c") ) {
        if(argc+1<args.length)
          command = args[argc+1];
        else
          printUsage();
        argc+=2;
        continue;
      } else if( args[argc].equals("-P") ) {
        if(argc+1<args.length)
          P = Integer.parseInt(args[argc+1]);
        else
          printUsage();
        argc+=2;
        continue;
      } else if( args[argc].equals("-s") ) {
        if(argc+1<args.length) {
          String sz[] = args[argc+1].split("x");
          if(sz.length==3) {
            W = Integer.parseInt(sz[0]);
            H = Integer.parseInt(sz[1]);
            S = Integer.parseInt(sz[2]);
          } else
            printUsage();
        } else
          printUsage();
        argc+=2;
        continue;
      } else {
        System.out.println("Invalid option " + args[argc]);
        printUsage();
      }

    }

    final MainPanel f = new MainPanel(uh[1],uh[0],password,W,H,S);
    f.setExitOnClose(true);
    f.setSSHPort(P);
    f.setAnswerYes(yes);
    f.setX11Forwarding(X11);
    f.setCommand(command);
    f.setVisible(true);

  }

}
