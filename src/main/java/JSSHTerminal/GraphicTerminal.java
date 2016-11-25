package JSSHTerminal;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class GraphicTerminal extends JComponent implements MouseListener,MouseMotionListener {

  private final Color[] defaultColors = {
      Color.BLACK,
      Color.RED.darker(),
      Color.GREEN.darker(),
      Color.ORANGE.darker(),
      Color.BLUE,
      Color.MAGENTA,
      Color.CYAN.darker(),
      Color.WHITE
  };

  private final Color cursorBackground = new Color(0,170,0);

  private SSHSession session = null;
  private TerminalEmulator terminal = null;

  private BufferedImage charSetA;
  private BufferedImage charSetB;
  private BufferedImage charSet0;
  private BufferedImage screen;
  private BufferedImage tmpImg;
  private int[] tmpBuffer;
  private int charWidth;
  private int charHeight;
  private int termWidth=0;
  private int termHeight=0;
  private int scrollPos=0;
  private Dimension size;
  private final MainPanel _parent;
  private int startSel=-1;
  private int endSel=-1;
  private boolean isDragging;
  private int lastChx=-1;
  private int lastChy=-1;
  private int lastBg=-1;
  private int lastFg=-1;
  private int lastCharSet=-1;
  private Graphics tmpImgG;

  public GraphicTerminal(MainPanel parent,int width,int height) {

    _parent = parent;
    setBorder(null);
    setOpaque(false);
    setDoubleBuffered(false);
    setFocusable(true);
    setFocusTraversalKeysEnabled(false);

    charWidth = 8;
    charHeight = 16;
    tmpImg = new BufferedImage(charWidth,charHeight,BufferedImage.TYPE_INT_RGB);
    tmpImgG = tmpImg.getGraphics();
    tmpBuffer = new int[charWidth*charHeight];
    loadFont();
    sizeComponent(width,height);

    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        Dimension d = getSize();
        if((d.width%charWidth==0) && (d.height%charHeight==0)) {
          sizeComponent(d.width/charWidth,d.height/charHeight);
        } else {
          setSize(charWidth*(d.width/charWidth),charHeight*(d.height/charHeight));
        }
      }
    });

    addMouseListener(this);
    addMouseMotionListener(this);

  }

  private void loadFont() {

    try {
      charSetA = ImageIO.read(getClass().getClassLoader().getResource("JSSHTerminal/fontPA.png"));
      charSetB = ImageIO.read(getClass().getClassLoader().getResource("JSSHTerminal/fontPB.png"));
      charSet0 = ImageIO.read(getClass().getClassLoader().getResource("JSSHTerminal/fontP0.png"));
    } catch (IOException e) {
      System.out.println("Cannot load font " + e.getMessage());
    }

  }

  public void dispose() {

    tmpImgG.dispose();
    tmpImg = null;
    screen = null;

  }


  public synchronized void sizeComponent(int width,int height) {

    if(termHeight!=height || termWidth!=width ) {
      // Resize
      termWidth = width;
      termHeight = height;
      //GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      //GraphicsDevice device = env.getDefaultScreenDevice();
      //GraphicsConfiguration config = device.getDefaultConfiguration();
      //screen = config.createCompatibleImage(termWidth*charWidth,termHeight*charHeight,Transparency.OPAQUE);
      screen = new BufferedImage(termWidth*charWidth,termHeight*charHeight, BufferedImage.TYPE_INT_RGB);
      size = new Dimension(termWidth*charWidth,termHeight*charHeight);
      notifySizeChange();
    }

  }

  public void notifySizeChange() {
    if(terminal!=null) terminal.resize(termWidth,termHeight);
    if(session!=null) session.resize(termWidth,termHeight);
  }

  public Dimension getPreferredSize() {
    return size;
  }


  public void setSession(SSHSession session) {

    this.session = session;
    terminal = session.getTerminal();

  }

  public void setScrollPos(int pos) {

    scrollPos = terminal.getScrollSize()-pos-termHeight;
    render();

  }

  public void moveScroll(int move) {

    scrollPos+=move;
    if(scrollPos<0) scrollPos=0;
    if(scrollPos>terminal.getScrollSize()-termHeight) scrollPos=terminal.getScrollSize()-termHeight;
    render();

  }

  public void paint(Graphics g) {

    //long t0 = System.currentTimeMillis();
    g.drawImage(screen,0,0,null);
    //long t1 = System.currentTimeMillis();
    //System.out.println("Paint time = " + (t1-t0) + "ms");

  }

  public synchronized void render() {

    //if(terminal!=null)
    //  System.out.println(terminal.dump());

    //long t0 = System.currentTimeMillis();

    Graphics g = screen.getGraphics();

    if (terminal == null) {
      g.setColor(Color.BLACK);
      g.drawRect(0, 0, termWidth * charWidth, termHeight * charHeight);
      return;
    }

    int[] scr = terminal.getScreen(scrollPos);

    int i = 0;
    for (int y = 0; y < termHeight; y++) {
      for (int x = 0; x < termWidth; x++) {

        int c = scr[i] & 0xFFFF;
        int sgr = scr[i] >> 16;

        int bg = (sgr & 0x70) >> 4;
        int fg = sgr & 0x7;
        boolean bold = (sgr & 0x8) != 0;
        boolean underline = (sgr & 0x80) != 0;
        boolean reverse = (sgr & 0x400) != 0;
        int charSet = (sgr & 0x300) >> 8;
        boolean isDefault = (fg == 7 && bg == 0) && !reverse;

        Color fgColor;
        Color bgColor;

        if (reverse) {
          bgColor = defaultColors[fg];
          fgColor = defaultColors[bg];
        } else {
          fgColor = defaultColors[fg];
          bgColor = defaultColors[bg];
        }

        if (bold) {
          if (fg == 0)
            fgColor = Color.GRAY;
          else
            fgColor = fgColor.brighter();
        }

        if (i - scrollPos * termWidth >= terminal.getStartSelection() &&
            i - scrollPos * termWidth <= terminal.getEndSelection()) {
          // Selected text
          bgColor = Color.WHITE;
          fgColor = Color.BLACK;
          isDefault = false;
        }

        if (terminal.isCursor(x, y - scrollPos)) {
          // Cursor
          bgColor = cursorBackground;
          fgColor = Color.BLACK;
          isDefault = false;
        }

        if (c < 256) {

          getChar(c, fgColor, bgColor, charSet, bold, isDefault);

        } else {


          switch (c) {

            case 0x2500: // hline
              c = 'q';
              charSet = 2;
              break;

            case 0x2502: // vline
              c = 'x';
              charSet = 2;
              break;

            case 0x250C: // LU corner
              c = 'l';
              charSet = 2;
              break;

            case 0x2510: // RU corner
              c = 'k';
              charSet = 2;
              break;

            case 0x2514: // LD corner
              c = 'm';
              charSet = 2;
              break;

            case 0x2518: // LD corner
              c = 'j';
              charSet = 2;
              break;

            case 0x2524: // T left
              c = 'u';
              charSet = 2;
              break;

            case 0x251C: // T right
              c = 't';
              charSet = 2;
              break;

            case 0x2592: // Dotted fill
              c = 'a';
              charSet = 2;
              break;
            case 0x2010: // Dash
              c = '-';
              break;

            default:
              // Not handled unicode
              System.out.println("Warning, unhandled unicode " + String.format("%04X", c));
              c = 0;
          }

        }

        getChar(c, fgColor, bgColor, charSet, bold, isDefault);
        g.drawImage(tmpImg, x * charWidth, y * charHeight, charWidth * (x + 1), charHeight * (y + 1),
            0, 0, charWidth, charHeight, null);

        if (underline) {
          g.setColor(Color.WHITE);
          g.drawLine(x * charWidth, (y + 1) * charHeight - 1, (x + 1) * charWidth, (y + 1) * charHeight - 1);
        }

        i++;
      }
    }


    //long t1 = System.currentTimeMillis();
    //System.out.println("Rendering time = " + (t1-t0) + "ms");

    g.dispose();
    repaint();
    _parent.updateScrollBar(scrollPos, terminal.getScrollSize(), termHeight);

  }

  BufferedImage getChar(int c,Color fgColor,Color bgColor,int charSet,boolean isBold,boolean isDefault) {

    // Character coordinates
    int chx = 0;
    int chy = 0;
    if(c>=32 && c<127) {
      chx = c % 16;
      chy = c / 16 - 2;
      if( isBold ) chy += 6;
    }

    // Detect context change
    int bg = bgColor.getRGB();
    int fg = fgColor.getRGB();

    if(lastBg==bg && lastFg==fg && lastChx==chx && lastChy==chy && lastCharSet==charSet) {
      // Last image is the same
      return tmpImg;
    }

    lastBg = bg;
    lastFg = fg;
    lastChx = chx;
    lastChy = chy;
    lastCharSet = charSet;

    BufferedImage src;

    switch (charSet) {
      case 1:
        src = charSetA;
        break;
      case 2:
        src = charSet0;
        break;
      default:
        src = charSetB;
    }

    if( isDefault ) {

      // Default white on black
      tmpImgG.drawImage(src, 0, 0, charWidth, charHeight,
          chx * charWidth, chy * charHeight, charWidth * (chx + 1), charHeight * (chy + 1), null);

    } else {

      if (c <= 32) {

        // Space (only paint background)
        tmpImgG.setColor(bgColor);
        tmpImgG.fillRect(0, 0, charWidth, charHeight);

      } else {

        // Colorize character
        double bgR = (double) bgColor.getRed();
        double bgG = (double) bgColor.getGreen();
        double bgB = (double) bgColor.getBlue();
        double fgR = (double) fgColor.getRed();
        double fgG = (double) fgColor.getGreen();
        double fgB = (double) fgColor.getBlue();
        double i255 = 1/255.0;

        src.getRGB(chx * charWidth, chy * charHeight, charWidth, charHeight, tmpBuffer, 0, charWidth);
        for (int i = 0; i < tmpBuffer.length; i++) {
          if ((tmpBuffer[i] & 0xFFFFFF) == 0) {
            tmpBuffer[i] = bg;
          } else if (tmpBuffer[i] == 0xFFFFFF) {
            tmpBuffer[i] = fg;
          } else {
            double factor = (double) (tmpBuffer[i] & 0xFF) * i255;
            double ofactor = 1.0 - factor;
            int nRed =   (int) (factor * fgR + ofactor * bgR) << 16;
            int nGreen = (int) (factor * fgG + ofactor * bgG) << 8;
            int nBlue =  (int) (factor * fgB + ofactor * bgB);
            tmpBuffer[i] = nRed | nGreen | nBlue;
          }
        }
        tmpImg.setRGB(0, 0, charWidth, charHeight, tmpBuffer, 0, charWidth);

      }

    }


    return tmpImg;

  }


  public void processKeyEvent(KeyEvent e) {

    int id=e.getID();
    if(id==KeyEvent.KEY_PRESSED && session!=null)
      keyPressed(e);
    e.consume();

  }

  public void keyPressed(KeyEvent e) {

    int keycode=e.getKeyCode();
    byte[] code=null;

    switch(keycode){
      case KeyEvent.VK_CONTROL:
      case KeyEvent.VK_SHIFT:
      case KeyEvent.VK_ALT:
      case KeyEvent.VK_CAPS_LOCK:
        return;
      case KeyEvent.VK_ENTER:
        code= TerminalEmulator.getCodeENTER();
        break;
      case KeyEvent.VK_UP:
        code= TerminalEmulator.getCodeUP();
        break;
      case KeyEvent.VK_DOWN:
        code= TerminalEmulator.getCodeDOWN();
        break;
      case KeyEvent.VK_RIGHT:
        code= TerminalEmulator.getCodeRIGHT();
        break;
      case KeyEvent.VK_LEFT:
        code= TerminalEmulator.getCodeLEFT();
        break;
      case KeyEvent.VK_F1:
        code= TerminalEmulator.getCodeF1();
        break;
      case KeyEvent.VK_F2:
        code= TerminalEmulator.getCodeF2();
        break;
      case KeyEvent.VK_F3:
        code= TerminalEmulator.getCodeF3();
        break;
      case KeyEvent.VK_F4:
        code= TerminalEmulator.getCodeF4();
        break;
      case KeyEvent.VK_F5:
        code= TerminalEmulator.getCodeF5();
        break;
      case KeyEvent.VK_F6:
        code= TerminalEmulator.getCodeF6();
        break;
      case KeyEvent.VK_F7:
        code= TerminalEmulator.getCodeF7();
        break;
      case KeyEvent.VK_F8:
        code= TerminalEmulator.getCodeF8();
        break;
      case KeyEvent.VK_F9:
        code= TerminalEmulator.getCodeF9();
        break;
      case KeyEvent.VK_F10:
        code= TerminalEmulator.getCodeF10();
        break;
      case KeyEvent.VK_F11:
        code= TerminalEmulator.getCodeF11();
        break;
      case KeyEvent.VK_F12:
        code= TerminalEmulator.getCodeF12();
        break;
      case KeyEvent.VK_TAB:
        code= TerminalEmulator.getCodeTAB();
        break;
    }

    if(code!=null){
      try{
        session.write(code);
      } catch(Exception ee){
        ee.printStackTrace();
      }
      scrollPos = 0;
      return;
    }

    char keychar=e.getKeyChar();
    if((keychar&0xff00)==0){
      try {
        session.write(new byte[]{(byte) e.getKeyChar()});
        scrollPos = 0;
      }
      catch(Exception ee){
      }
    }

  }

  private void sendMouseEvent(int x,int y,int b,boolean press) {

    if(b<1)
      return;

    char Cx = (char)(x+33);
    char Cy = (char)(y+33);
    char Cb;

    if(press)
      Cb = (char)(b+31);
    else
      Cb = (char)(35);

    String seq = "\u001B[M" + Cb + Cx + Cy;
    session.write(seq);

  }

  private void copyToClipboard(String s) {

    if(s.length()>0) {
      StringSelection stringSelection = new StringSelection( s );
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents( stringSelection, null );
    }

  }

  private String getFromClipboard() {

    String ret = "";
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    try {
      ret = (String)(clipboard.getData(DataFlavor.stringFlavor));
    }
    catch (UnsupportedFlavorException e1) {}
    catch (IOException e2) {}
    return ret;

  }

  @Override
  public void mouseClicked(MouseEvent e) {

    if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount()==2) {
      int sX = e.getX() / charWidth;
      int sY = e.getY() / charHeight - scrollPos;
      int offset = sX + sY * termWidth;
      terminal.autoSelect(offset);
      copyToClipboard(terminal.getSelectedText());
      render();
    }

    if(SwingUtilities.isMiddleMouseButton(e) && e.getClickCount()==1) {
      String s = getFromClipboard();
      session.write(s);
      scrollPos=0;
    }

  }

  @Override
  public void mousePressed(MouseEvent e) {

    int sX = e.getX() / charWidth;
    int sY = e.getY() / charHeight - scrollPos;

    if(SwingUtilities.isLeftMouseButton(e)) {
      startSel = sX + sY * termWidth;
      terminal.clearSelection();
      isDragging = false;
      render();
    }

    if(terminal.isMouseEnabled())
      sendMouseEvent(sX,sY,e.getButton(),true);

  }

  @Override
  public void mouseReleased(MouseEvent e) {

    int sX = e.getX() / charWidth;
    int sY = e.getY() / charHeight - scrollPos;

    if(SwingUtilities.isLeftMouseButton(e) && isDragging) {
      endSel = sX + sY * termWidth;
      terminal.setSelection(startSel, endSel);
      copyToClipboard(terminal.getSelectedText());
      isDragging = false;
      render();
    }

    if(terminal.isMouseEnabled())
      sendMouseEvent(sX,sY,e.getButton(),false);

  }

  @Override
  public void mouseEntered(MouseEvent e) {

  }

  @Override
  public void mouseExited(MouseEvent e) {

  }

  @Override
  public void mouseDragged(MouseEvent e) {

   if(SwingUtilities.isLeftMouseButton(e)) {
      isDragging = true;
      int sX = e.getX() / charWidth;
      int sY = e.getY() / charHeight - scrollPos;
      endSel = sX + sY * termWidth;
      terminal.setSelection(startSel,endSel);
      render();
    }

  }

  @Override
  public void mouseMoved(MouseEvent e) {

  }

}
