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

  //private GraphicTerminal textArea;
  private TerminalEvent textArea;
  private JScrollBar    scrollBar;
  private SSHSession    session;
  private String        _host;
  private String        _user;
  private String        _password;
  private boolean       exitOnClose = false;
  private boolean       scrollUpdate;

  public final static double JTERM_RELEASE = 1.0 ; // Let the space before the ';'

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

    String OS_NAME = System.getProperty("os.name");
    String _OS_NAME = OS_NAME.toLowerCase();
    boolean isWindows = _OS_NAME.startsWith("windows");

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
    setTitle("JSSHTerminal " + user + "@" + host);

    addWindowListener(new WindowAdapter() {

      @Override
      public void windowOpened(WindowEvent e) {
        try {
          session.connect(_host, _user, _password);
          textArea.notifySizeChange();
        } catch (IOException ex) {
          JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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

    System.out.println("Usage: jterminal username@host [-p password] [-y] [-s WxHxS] [-X]");
    System.out.println("       username@host username used to login on host");
    System.out.println("       -p password password used to login");
    System.out.println("       -y Answer yes to question");
    System.out.println("       -s WxHxS terminal size WidthxHeightxScrollbar");
    System.out.println("       -X Enable X11 forwarding");
    System.exit(0);

  }

  public static void main(String[] args) {

    int W = 80;
    int H = 24;
    int S = 500;
    String password = null;
    boolean yes = false;
    boolean X11 = false;

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
    f.setAnswerYes(yes);
    f.setX11Forwarding(X11);
    f.setVisible(true);

  }

}
