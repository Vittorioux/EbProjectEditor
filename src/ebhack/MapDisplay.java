package ebhack;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Stack;

public class MapDisplay extends AbstractButton implements
        ActionListener, MouseListener, MouseMotionListener {
    private YMLPreferences prefs;
    private MapData map;
    private JMenuItem copySector, pasteSector, copySector2, pasteSector2,
            undoButton, redoButton;

    private final ActionEvent sectorEvent = new ActionEvent(this,
            ActionEvent.ACTION_PERFORMED, "sectorChanged");

    private static Image[][][] tileImageCache;

    private class UndoableTileChange {
        public int x, y, oldTile, newTile;

        public UndoableTileChange(int x, int y, int oldTile, int newTile) {
            this.x = x;
            this.y = y;
            this.oldTile = oldTile;
            this.newTile = newTile;
        }
    }

    private class UndoableSectorPaste {
        public int sectorX, sectorY;
        private int[][] tiles;
        private MapData.Sector sector;

        public UndoableSectorPaste(int sectorX, int sectorY, int[][] tiles,
                                   MapData.Sector sector) {
            this.sectorX = sectorX;
            this.sectorY = sectorY;
            this.tiles = tiles;
            this.sector = sector;
        }

    }

    private Stack<Object> undoStack = new Stack<Object>();
    private Stack<Object> redoStack = new Stack<Object>();

    private int screenWidth = 24;
    private int screenHeight = 12;

    // Map X and Y coordinates of the tile displayed in the top left corner
    private int x = 0, y = 0;
    // Data for the selected sector
    private MapData.Sector selectedSector = null;
    private int sectorX, sectorY;
    private int sectorPal;
    private boolean grid = true;
    private boolean spriteBoxes = true;

    // Moving stuff
    private int movingDrawX, movingDrawY;
    private int movingNPC = -1;
    private Image movingNPCimg;
    private Integer[] movingNPCdim;
    private MapData.Door movingDoor = null;

    // Popup menus
    private int popupX, popupY;
    private JPopupMenu spritePopupMenu, doorPopupMenu;
    private JMenuItem detailsNPC, delNPC, cutNPC, copyNPC, switchNPC, moveNPC;
    private int copiedNPC = 0;
    private MapData.SpriteEntry popupSE;
    private JMenuItem detailsDoor, delDoor, cutDoor, copyDoor, editDoor,
            jumpDoor;
    private MapData.Door popupDoor, copiedDoor;

    // Seeking stuff
    private int seekDrawX, seekDrawY;
    private DoorEditor doorSeeker;

    // Editing hotspot
    private MapData.Hotspot editHS = null;
    private int editHSx1, editHSy1;
    private int hsMouseX, hsMouseY;

    // Mode settings
    private MapMode currentMode = MapMode.MAP;
    private MapMode previousMode = null;
    private boolean drawTileNums = false;
    private boolean drawSpriteNums = true;
    private boolean gamePreview = false;
    private boolean tvPreview = false;
    private int tvPreviewX, tvPreviewY, tvPreviewW, tvPreviewH;

    // Coordinate labels
    private JLabel pixelCoordLabel, warpCoordLabel, tileCoordLabel;

    // Cache enemy colors
    public static Color[] enemyColors = null;

    private MapTileSelector tileSelector;

    public MapDisplay(MapData map, JMenuItem copySector,
                      JMenuItem pasteSector, JMenuItem copySector2,
                      JMenuItem pasteSector2, JMenuItem undoButton,
                      JMenuItem redoButton, JLabel pixelCoordLabel,
                      JLabel warpCoordLabel, JLabel tileCoordLabel,
                      YMLPreferences prefs) {
        super();

        if (enemyColors == null) {
            enemyColors = new Color[203];
            for (int i = 0; i < 203; ++i)
                enemyColors[i] = new Color(
                        ((int) (Math.E * 0x100000 * i)) & 0xffffff);
        }

        this.prefs = prefs;

        this.map = map;
        this.copySector = copySector;
        this.pasteSector = pasteSector;
        this.copySector2 = copySector2;
        this.pasteSector2 = pasteSector2;
        this.undoButton = undoButton;
        this.redoButton = redoButton;
        this.pixelCoordLabel = pixelCoordLabel;
        this.warpCoordLabel = warpCoordLabel;
        this.tileCoordLabel = tileCoordLabel;

        if (tileImageCache == null)
            resetTileImageCache();

        // Create Sprite popup menu
        spritePopupMenu = new JPopupMenu();
        spritePopupMenu.add(detailsNPC = ToolModule.createJMenuItem(
                "Sprite @ ", ' ', null, null, this));
        detailsNPC.setEnabled(false);
        spritePopupMenu.add(ToolModule.createJMenuItem("New NPC", 'n',
                null, "newNPC", this));
        spritePopupMenu.add(delNPC = ToolModule.createJMenuItem(
                "Delete NPC", 'd', null, "delNPC", this));
        spritePopupMenu.add(cutNPC = ToolModule.createJMenuItem("Cut NPC",
                'c', null, "cutNPC", this));
        spritePopupMenu.add(copyNPC = ToolModule.createJMenuItem(
                "Copy NPC", 'y', null, "copyNPC", this));
        spritePopupMenu.add(ToolModule.createJMenuItem("Paste NPC", 'p',
                null, "pasteNPC", this));
        spritePopupMenu.add(switchNPC = ToolModule.createJMenuItem(
                "Switch NPC", 's', null, "switchNPC", this));
        spritePopupMenu.add(moveNPC = ToolModule.createJMenuItem(
                "Move NPC", 'm', null, "moveNPC", this));

        // Create Door popup menu
        doorPopupMenu = new JPopupMenu();
        doorPopupMenu.add(detailsDoor = ToolModule.createJMenuItem(
                "Door @ ", ' ', null, null, this));
        detailsDoor.setEnabled(false);
        doorPopupMenu.add(ToolModule.createJMenuItem("New Door", 'n', null,
                "newDoor", this));
        doorPopupMenu.add(delDoor = ToolModule.createJMenuItem(
                "Delete Door", 'd', null, "delDoor", this));
        doorPopupMenu.add(cutDoor = ToolModule.createJMenuItem("Cut Door",
                'c', null, "cutDoor", this));
        doorPopupMenu.add(copyDoor = ToolModule.createJMenuItem(
                "Copy Door", 'y', null, "copyDoor", this));
        doorPopupMenu.add(ToolModule.createJMenuItem("Paste Door", 'p',
                null, "pasteDoor", this));
        doorPopupMenu.add(editDoor = ToolModule.createJMenuItem(
                "Edit Door", 'e', null, "editDoor", this));
        doorPopupMenu.add(jumpDoor = ToolModule.createJMenuItem(
                "Jump to Destination", 'j', null, "jumpDoor", this));

        addMouseListener(this);
        addMouseMotionListener(this);

        setPreferredSize(new Dimension(
                screenWidth * MapData.TILE_WIDTH + 2, screenHeight
                * MapData.TILE_HEIGHT + 2));
    }

    public void init() {
        selectSector(0, 0);
        changeMode(MapMode.MAP);
        reset();
    }

    public void reset() {
        undoStack.clear();
        undoButton.setEnabled(false);
        redoStack.clear();
        redoButton.setEnabled(false);
    }

    public void setTileSelector(MapTileSelector tileSelector) {
        this.tileSelector = tileSelector;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        if (isEnabled())
            drawMap(g2d);
        else {
            // Draw border
            g2d.setColor(Color.black);
            g2d.draw(new Rectangle2D.Double(0, 0, screenWidth
                    * MapData.TILE_WIDTH + 2, screenHeight
                    * MapData.TILE_HEIGHT + 2));
        }
    }

    private void drawMap(Graphics2D g) {
        int i, j, a;
        g.setPaint(Color.white);
        g.setFont(new Font("Arial", Font.PLAIN, 12));

        MapData.Sector sector;
        int pal;
        for (i = 0; i < screenHeight; i++) {
            for (j = 0; j < screenWidth; j++) {
                sector = map.getSector((j + x) / MapData.SECTOR_WIDTH,
                        (i + y) / MapData.SECTOR_HEIGHT);
                pal = TileEditor.tilesets[TileEditor
                        .getDrawTilesetNumber(sector.tileset)]
                        .getPaletteNum(sector.tileset, sector.palette);
                g.drawImage(
                        getTileImage(TileEditor
                                .getDrawTilesetNumber(sector.tileset), map
                                .getMapTile(x + j, y + i), pal), j
                                * MapData.TILE_WIDTH + 1, i
                                * MapData.TILE_HEIGHT + 1,
                        MapData.TILE_WIDTH, MapData.TILE_HEIGHT, this);
                if (drawTileNums && !gamePreview) {
                    drawNumber(g, map.getMapTile(x + j, y + i), j
                            * MapData.TILE_WIDTH + 1, i
                            * MapData.TILE_HEIGHT + 1, false, false);
                }
            }
        }

        if (grid && !gamePreview && !currentMode.drawEnemies())
            drawGrid(g);

        if (currentMode == MapMode.MAP && (selectedSector != null)) {
            int sXt, sYt;
            if (((sXt = sectorX * MapData.SECTOR_WIDTH)
                    + MapData.SECTOR_WIDTH >= x)
                    && (sXt < x + screenWidth)
                    && ((sYt = sectorY * MapData.SECTOR_HEIGHT)
                    + MapData.SECTOR_HEIGHT >= y)
                    && (sYt < y + screenHeight)) {
                g.setPaint(Color.yellow);
                g.draw(new Rectangle2D.Double((sXt - x)
                        * MapData.TILE_WIDTH + 1, (sYt - y)
                        * MapData.TILE_HEIGHT + 1, MapData.SECTOR_WIDTH
                        * MapData.TILE_WIDTH, MapData.SECTOR_HEIGHT
                        * MapData.TILE_HEIGHT));
            }
        }

        // Draw border
        g.setColor(Color.black);
        g.draw(new Rectangle2D.Double(0, 0, screenWidth
                * MapData.TILE_WIDTH + 2, screenHeight
                * MapData.TILE_HEIGHT + 2));

        if (currentMode.drawSprites()) {
            MapData.NPC npc;
            Integer[] wh;
            java.util.List<MapData.SpriteEntry> area;
            for (i = y & (~7); i < (y & (~7)) + screenHeight + 8; i += 8) {
                for (j = x & (~7); j < (x & (~7)) + screenWidth + 8; j += 8) {
                    try {
                        area = map.getSpriteArea(j >> 3, i >> 3);
                        for (MapData.SpriteEntry e : area) {
                            npc = map.getNPC(e.npcID);
                            wh = map.getSpriteWH(npc.sprite);
                            if (spriteBoxes && !gamePreview) {
                                g.setPaint(Color.RED);
                                g.draw(new Rectangle2D.Double(e.x + (j - x)
                                        * MapData.TILE_WIDTH - wh[0] / 2,
                                        e.y + (i - y) * MapData.TILE_HEIGHT
                                                - wh[1] + 8, wh[0] + 1,
                                        wh[1] + 1));
                            }
                            g.drawImage(map.getSpriteImage(npc.sprite,
                                            npc.direction), e.x + (j - x)
                                            * MapData.TILE_WIDTH - wh[0] / 2 + 1,
                                    e.y + (i - y) * MapData.TILE_HEIGHT
                                            - wh[1] + 9, this);
                            if (drawSpriteNums && !gamePreview) {
                                drawNumber(g, e.npcID, e.x + (j - x)
                                                * MapData.TILE_WIDTH - wh[0] / 2,
                                        e.y + (i - y) * MapData.TILE_HEIGHT
                                                - wh[1] + 8, false, true);
                            }
                        }
                    } catch (Exception e) {

                    }
                }
            }

            if (currentMode == MapMode.SPRITE && (movingNPC != -1)) {
                if (spriteBoxes) {
                    g.setPaint(Color.RED);
                    g.draw(new Rectangle2D.Double(movingDrawX - 1,
                            movingDrawY - 1, movingNPCdim[0] + 1,
                            movingNPCdim[1] + 1));
                }
                g.drawImage(movingNPCimg, movingDrawX, movingDrawY, this);
            }
        }

        if (currentMode.drawDoors()) {
            List<MapData.Door> area;
            for (i = y & (~7); i < (y & (~7)) + screenHeight + 8; i += 8) {
                for (j = x & (~7); j < (x & (~7)) + screenWidth + 8; j += 8) {
                    try {
                        area = map.getDoorArea(j >> 3, i >> 3);
                        for (MapData.Door e : area) {
                            g.setPaint(e.getColor());
                            g.draw(new Rectangle2D.Double(e.x * 8 + (j - x)
                                    * MapData.TILE_WIDTH + 1, e.y * 8
                                    + (i - y) * MapData.TILE_HEIGHT + 1, 8,
                                    8));
                            g.draw(new Rectangle2D.Double(e.x * 8 + (j - x)
                                    * MapData.TILE_WIDTH + 3, e.y * 8
                                    + (i - y) * MapData.TILE_HEIGHT + 3, 4,
                                    4));
                            g.setPaint(Color.WHITE);
                            g.draw(new Rectangle2D.Double(e.x * 8 + (j - x)
                                    * MapData.TILE_WIDTH + 2, e.y * 8
                                    + (i - y) * MapData.TILE_HEIGHT + 2, 6,
                                    6));
                            g.draw(new Rectangle2D.Double(e.x * 8 + (j - x)
                                    * MapData.TILE_WIDTH + 4, e.y * 8
                                    + (i - y) * MapData.TILE_HEIGHT + 4, 2,
                                    2));
                        }
                    } catch (Exception e) {

                    }
                }
            }

            if (currentMode == MapMode.DOOR && (movingDoor != null)) {
                g.setPaint(movingDoor.getColor());
                g.draw(new Rectangle2D.Double(movingDrawX + 1,
                        movingDrawY + 1, 8, 8));
                g.draw(new Rectangle2D.Double(movingDrawX + 3,
                        movingDrawY + 3, 4, 4));
                g.setPaint(Color.WHITE);
                g.draw(new Rectangle2D.Double(movingDrawX + 2,
                        movingDrawY + 2, 6, 6));
                g.draw(new Rectangle2D.Double(movingDrawX + 4,
                        movingDrawY + 4, 2, 2));
            }

            if (currentMode == MapMode.SEEK_DOOR) {
                g.setPaint(Color.WHITE);
                g.draw(new Rectangle2D.Double(seekDrawX + 1, seekDrawY + 1,
                        8, 8));
                g.draw(new Rectangle2D.Double(seekDrawX + 3, seekDrawY + 3,
                        4, 4));

                g.setPaint(new Color(57, 106, 177));
                g.draw(new Rectangle2D.Double(seekDrawX + 2, seekDrawY + 2,
                        6, 6));
                g.draw(new Rectangle2D.Double(seekDrawX + 4, seekDrawY + 4,
                        2, 2));
            }
        }

        if (currentMode.drawEnemies()) {
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            for (i = -(y % 2); i < screenHeight; i += 2) {
                for (j = -(x % 2); j < screenWidth; j += 2) {
                    // Draw the grid
                    Rectangle2D rect = new Rectangle2D.Double(j
                            * MapData.TILE_WIDTH + 1, i
                            * MapData.TILE_HEIGHT + 1,
                            MapData.TILE_WIDTH * 2, MapData.TILE_HEIGHT * 2);
                    if (grid && !gamePreview) {
                        g.setColor(Color.BLACK);
                        g.draw(rect);
                    }

                    a = map.getMapEnemyGroup((x + j) / 2, (y + i) / 2);
                    if (a != 0) {
                        g.setComposite(AlphaComposite.getInstance(
                                AlphaComposite.SRC_OVER, 0.5F));
                        g.setPaint(enemyColors[a]);
                        g.fill(rect);

                        g.setComposite(AlphaComposite.getInstance(
                                AlphaComposite.SRC_OVER, 1.0F));
                        drawNumber(g, a, j * MapData.TILE_WIDTH + 1, i
                                * MapData.TILE_HEIGHT + 1, false, false);
                    }
                }
            }
        }

        if (currentMode.drawHotspots()) {
            MapData.Hotspot hs;
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 0.8F));
            int tx1, ty1, tx2, ty2;
            for (i = 0; i < map.numHotspots(); ++i) {
                hs = map.getHotspot(i);
                if (hs == editHS)
                    continue;
                tx1 = hs.x1 / 4 - x;
                ty1 = hs.y1 / 4 - y;
                tx2 = hs.x2 / 4 - x;
                ty2 = hs.y2 / 4 - y;
                if (((tx1 >= 0) && (tx1 <= screenWidth) && (ty1 >= 0) && (ty1 <= screenHeight))
                        || ((tx2 >= 0) && (tx2 <= screenWidth)
                        && (ty2 >= 0) && (ty2 <= screenHeight))) {
                    g.setPaint(Color.PINK);
                    g.fill(new Rectangle2D.Double(hs.x1 * 8 - x
                            * MapData.TILE_WIDTH + 1, hs.y1 * 8 - y
                            * MapData.TILE_HEIGHT + 1, (hs.x2 - hs.x1) * 8,
                            (hs.y2 - hs.y1) * 8));

                    drawNumber(g, i,
                            hs.x1 * 8 - x * MapData.TILE_WIDTH + 1, hs.y1
                                    * 8 - y * MapData.TILE_HEIGHT + 1,
                            false, false);
                }
            }

            if (currentMode == MapMode.HOTSPOT && (editHS != null)) {
                g.setPaint(Color.WHITE);
                if (editHSx1 != -1) {
                    tx1 = editHSx1 * 8 - x * MapData.TILE_WIDTH + 1;
                    ty1 = editHSy1 * 8 - y * MapData.TILE_HEIGHT + 1;
                    g.fill(new Rectangle2D.Double(tx1, ty1, hsMouseX - tx1,
                            hsMouseY - ty1));
                } else {
                    g.fill(new Rectangle2D.Double(hsMouseX + 1,
                            hsMouseY + 1, 65, 65));
                }
            }
        }

        if (gamePreview && tvPreview) {
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 1.0F));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, tvPreviewX - tvPreviewW, screenHeight
                    * MapData.TILE_HEIGHT);
            g.fillRect(tvPreviewX + tvPreviewW, 0,
                    (screenWidth * MapData.TILE_WIDTH) - tvPreviewX
                            - tvPreviewW, screenHeight
                            * MapData.TILE_HEIGHT);
            g.fillRect(0, 0, screenWidth * MapData.TILE_WIDTH, tvPreviewY
                    - tvPreviewH);
            g.fillRect(0, tvPreviewY + tvPreviewH, screenWidth
                            * MapData.TILE_WIDTH,
                    (screenHeight * MapData.TILE_HEIGHT) - tvPreviewY
                            - tvPreviewH);

            // hardcoded for sprite of size 16,24
            g.drawImage(map.getSpriteImage(1, 2), tvPreviewX - 7,
                    tvPreviewY - 15, this);
        }
    }

    private Rectangle2D textBG;

    private void drawNumber(Graphics2D g, int n, int x, int y, boolean hex,
                            boolean above) {
        String s;
        if (hex)
            s = ToolModule.addZeros(Integer.toHexString(n), 4);
        else
            s = ToolModule.addZeros(Integer.toString(n), 4);

        if (textBG == null)
            textBG = g.getFontMetrics().getStringBounds(s, g);

        g.setPaint(Color.black);
        if (above) {
            textBG.setRect(x, y - textBG.getHeight(), textBG.getWidth(),
                    textBG.getHeight());
            g.fill(textBG);
            g.setPaint(Color.white);
            g.drawString(s, x, y);
        } else {
            textBG.setRect(x, y, textBG.getWidth(), textBG.getHeight());
            g.fill(textBG);
            g.setPaint(Color.white);
            g.drawString(s, x, y + ((int) textBG.getHeight()));
        }
    }

    private void drawGrid(Graphics2D g) {
        g.setPaint(Color.black);
        // Draw vertical lines
        for (int i = 0; i < screenWidth + 1; i++)
            g.drawLine(1 + i * MapData.TILE_WIDTH, 1, 1 + i
                    * MapData.TILE_WIDTH, screenHeight
                    * MapData.TILE_HEIGHT);
        // Draw horizontal lines
        for (int i = 0; i < screenHeight + 1; i++)
            g.drawLine(1, 1 + i * MapData.TILE_HEIGHT, screenWidth
                    * MapData.TILE_WIDTH, 1 + i * MapData.TILE_HEIGHT);

        // Blank pixel in the bottom right corner
        g.drawLine(screenWidth * MapData.TILE_WIDTH + 1, screenHeight
                * MapData.TILE_HEIGHT + 1, screenWidth * MapData.TILE_WIDTH
                + 1, screenHeight * MapData.TILE_HEIGHT + 1);
    }

    public static Image getTileImage(int loadtset, int loadtile,
                                     int loadpalette) {
        if (tileImageCache[loadtset][loadtile][loadpalette] == null) {
            try {
                tileImageCache[loadtset][loadtile][loadpalette] = TileEditor.tilesets[loadtset]
                        .getArrangementImage(loadtile, loadpalette);
            } catch (IndexOutOfBoundsException ioobe) {
                System.err.println("Invalid tset/tile/pal: " + loadtset
                        + "/" + loadtile + "/" + loadpalette);
                ioobe.printStackTrace();
            }
        }
        return tileImageCache[loadtset][loadtile][loadpalette];
    }

    public static void resetTileImageCache() {
        tileImageCache = new Image[TileEditor.NUM_TILESETS][1024][59];
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public MapData.Sector getSelectedSector() {
        return selectedSector;
    }

    public void setSelectedSectorTileset(int tset) {
        selectedSector.tileset = tset;
        sectorPal = TileEditor.tilesets[TileEditor
                .getDrawTilesetNumber(selectedSector.tileset)]
                .getPaletteNum(selectedSector.tileset,
                        selectedSector.palette);
    }

    public void setSelectedSectorPalette(int pal) {
        selectedSector.palette = pal;
        sectorPal = TileEditor.tilesets[TileEditor
                .getDrawTilesetNumber(selectedSector.tileset)]
                .getPaletteNum(selectedSector.tileset,
                        selectedSector.palette);
    }

    public int getSelectedSectorPalNumber() {
        return sectorPal;
    }

    public void setMapXY(int x, int y) {
        x = Math.max(0, x);
        y = Math.max(0, y);
        this.x = Math.min(x, MapData.WIDTH_IN_TILES - screenWidth);
        this.y = Math.min(y, MapData.HEIGHT_IN_TILES - screenHeight);
    }

    public void setMapX(int x) {
        setMapXY(x, y);
    }

    public void setMapY(int y) {
        setMapXY(x, y);
    }

    public int getMapX() {
        return x;
    }

    public int getMapY() {
        return y;
    }

    public int getSectorX() {
        return sectorX;
    }

    public int getSectorY() {
        return sectorY;
    }

    private void selectSector(int sX, int sY) {
        sectorX = sX;
        sectorY = sY;
        MapData.Sector newS = map.getSector(sectorX, sectorY);
        if (selectedSector != newS) {
            selectedSector = newS;
            sectorPal = TileEditor.tilesets[TileEditor
                    .getDrawTilesetNumber(selectedSector.tileset)]
                    .getPaletteNum(selectedSector.tileset,
                            selectedSector.palette);
            copySector.setEnabled(true);
            pasteSector.setEnabled(true);
            copySector2.setEnabled(true);
            pasteSector2.setEnabled(true);
        } else {
            // Un-select sector
            selectedSector = null;
            copySector.setEnabled(false);
            pasteSector.setEnabled(false);
            copySector2.setEnabled(false);
            pasteSector2.setEnabled(false);
        }
        repaint();
        this.fireActionPerformed(sectorEvent);
    }

    public void mouseClicked(MouseEvent e) {
        // Make sure they didn't click on the border
        if ((e.getX() >= 1)
                && (e.getX() <= screenWidth * MapData.TILE_WIDTH + 2)
                && (e.getY() >= 1)
                && (e.getY() <= screenHeight * MapData.TILE_HEIGHT + 2)) {
            if (currentMode == MapMode.MAP) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    int mX = (e.getX() - 1) / MapData.TILE_WIDTH + x;
                    int mY = (e.getY() - 1) / MapData.TILE_HEIGHT + y;
                    if (e.isShiftDown()) {
                        tileSelector.selectTile(map.getMapTile(mX, mY));
                    } else if (!e.isControlDown()) {
                        // Keep track of the undo stuff
                        undoStack.push(new UndoableTileChange(mX, mY, map
                                .getMapTile(mX, mY), tileSelector
                                .getSelectedTile()));
                        undoButton.setEnabled(true);
                        redoStack.clear();

                        map.setMapTile(mX, mY,
                                tileSelector.getSelectedTile());
                        repaint();
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // Make sure they didn't click on the border
                    int sX = (x + ((e.getX() - 1) / MapData.TILE_WIDTH))
                            / MapData.SECTOR_WIDTH;
                    int sY = (y + ((e.getY() - 1) / MapData.TILE_HEIGHT))
                            / MapData.SECTOR_HEIGHT;
                    selectSector(sX, sY);
                }
            } else if (currentMode == MapMode.SPRITE) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popupX = e.getX();
                    popupY = e.getY();
                    popupSE = getSpriteEntryFromMouseXY(e.getX(), e.getY());
                    if (popupSE == null) {
                        detailsNPC.setText("No Sprite Selected");
                        delNPC.setEnabled(false);
                        cutNPC.setEnabled(false);
                        copyNPC.setEnabled(false);
                        switchNPC.setText("Switch NPC");
                        switchNPC.setEnabled(false);
                        moveNPC.setEnabled(false);
                    } else {
                        final int areaX = ((x + popupX / MapData.TILE_WIDTH) / 8)
                                * MapData.TILE_WIDTH * 8;
                        final int areaY = ((y + popupY
                                / MapData.TILE_HEIGHT) / 8)
                                * MapData.TILE_HEIGHT * 8;
                        detailsNPC.setText("Sprite @ ("
                                + (areaX + popupSE.x) + ","
                                + (areaY + popupSE.y) + ")");
                        delNPC.setEnabled(true);
                        cutNPC.setEnabled(true);
                        copyNPC.setEnabled(true);
                        switchNPC.setText("Switch NPC (" + popupSE.npcID
                                + ")");
                        switchNPC.setEnabled(true);
                        moveNPC.setEnabled(true);
                    }
                    spritePopupMenu.show(this, e.getX(), e.getY());
                }
            } else if (currentMode == MapMode.DOOR) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popupX = e.getX();
                    popupY = e.getY();
                    popupDoor = getDoorFromMouseXY(e.getX(), e.getY());
                    if (popupDoor == null) {
                        detailsDoor.setText("No Door Selected");
                        delDoor.setEnabled(false);
                        cutDoor.setEnabled(false);
                        copyDoor.setEnabled(false);
                        editDoor.setEnabled(false);
                        jumpDoor.setEnabled(false);
                    } else {
                        final int areaX = ((x + popupX / MapData.TILE_WIDTH) / MapData.SECTOR_WIDTH)
                                * MapData.SECTOR_WIDTH * (MapData.TILE_WIDTH / 8);
                        final int areaY = ((y + popupY / MapData.TILE_HEIGHT) / (MapData.SECTOR_HEIGHT * 2))
                                * MapData.SECTOR_HEIGHT * (MapData.TILE_HEIGHT / 8);
                        detailsDoor.setText(ToolModule
                                .capitalize(popupDoor.type)
                                + " @ ("
                                + (areaX + popupDoor.x)
                                + ","
                                + (areaY + popupDoor.y) + ")");
                        delDoor.setEnabled(true);
                        cutDoor.setEnabled(true);
                        copyDoor.setEnabled(true);
                        editDoor.setEnabled(true);
                        jumpDoor.setEnabled(popupDoor.type.equals("door"));

                    }
                    doorPopupMenu.show(this, e.getX(), e.getY());
                }
            } else if (currentMode == MapMode.SEEK_DOOR) {
                doorSeeker.seek(x * 4 + seekDrawX / 8, y * 4 + seekDrawY
                        / 8);
                doorSeeker = null;
                changeMode(currentMode);
                repaint();
            } else if (currentMode == MapMode.ENEMY) {
                int eX = ((e.getX() - 1) / MapData.TILE_WIDTH + x) / 2;
                int eY = ((e.getY() - 1) / MapData.TILE_HEIGHT + y) / 2;
                if (e.isShiftDown()) {
                    tileSelector.selectTile(map.getMapEnemyGroup(eX, eY));
                } else {
                    map.setMapEnemyGroup(eX, eY,
                            tileSelector.getSelectedTile());
                    repaint();
                }
            } else if (currentMode == MapMode.HOTSPOT) {
                int mx = ((e.getX() - 1) / 8) + (x * 4), my = ((e.getY() - 1) / 8)
                        + (y * 4);
                if (editHS != null) {
                    if (editHSx1 == -1) {
                        editHSx1 = mx;
                        editHSy1 = my;
                        repaint();
                    } else {
                        editHS.x1 = editHSx1;
                        editHS.y1 = editHSy1;
                        editHS.x2 = mx;
                        editHS.y2 = my;
                        editHS = null;
                        repaint();
                    }
                } else {
                    for (int i = 0; i < map.numHotspots(); ++i) {
                        MapData.Hotspot hs = map.getHotspot(i);
                        if ((mx >= hs.x1) && (mx <= hs.x2) && (my >= hs.y1)
                                && (my <= hs.y2)) {
                            editHS = hs;
                            editHSx1 = editHSy1 = -1;
                            hsMouseX = e.getX() & (~7);
                            hsMouseY = e.getY() & (~7);
                            repaint();
                            return;
                        }
                    }
                }
            }
        }
    }

    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("newNPC")) {
            pushNpcIdFromMouseXY(0, popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("delNPC")) {
            popNpcIdFromMouseXY(popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("cutNPC")) {
            copiedNPC = popNpcIdFromMouseXY(popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("copyNPC")) {
            copiedNPC = popupSE.npcID;
        } else if (ae.getActionCommand().equals("pasteNPC")) {
            pushNpcIdFromMouseXY(copiedNPC, popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("switchNPC")) {
            String input = JOptionPane.showInputDialog(this,
                    "Switch this to a different NPC", popupSE.npcID);
            if (input != null) {
                popupSE.npcID = Integer.parseInt(input);
                repaint();
            }
        } else if (ae.getActionCommand().equals("moveNPC")) {
            int areaX = (x + popupX / MapData.TILE_WIDTH) / 8;
            int areaY = (y + popupY / MapData.TILE_HEIGHT) / 8;

            final int newSpriteX, newSpriteY;

            String input = JOptionPane.showInputDialog(this,
                    "New X in pixels", areaX * MapData.TILE_WIDTH * 8 + popupSE.x);
            if (input == null)
                return;
            newSpriteX = Integer.parseInt(input);

            input = JOptionPane.showInputDialog(this,
                    "New Y in pixels", areaY * MapData.TILE_HEIGHT * 8 + popupSE.y);
            if (input == null)
                return;
            newSpriteY = Integer.parseInt(input);

            popNpcIdFromMouseXY(popupX, popupY);
            pushNpcIdFromMapPixelXY(popupSE.npcID, newSpriteX, newSpriteY);

            repaint();
        } else if (ae.getActionCommand().equals("newDoor")) {
            pushDoorFromMouseXY(new MapData.Door(), popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("delDoor")) {
            popDoorFromMouseXY(popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("cutDoor")) {
            copiedDoor = popDoorFromMouseXY(popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("copyDoor")) {
            copiedDoor = popupDoor.copy();
        } else if (ae.getActionCommand().equals("pasteDoor")) {
            pushDoorFromMouseXY(copiedDoor.copy(), popupX, popupY);
            repaint();
        } else if (ae.getActionCommand().equals("editDoor")) {
            Ebhack.main.showModule(DoorEditor.class, popupDoor);
        } else if (ae.getActionCommand().equals("jumpDoor")) {
            Ebhack.main.showModule(MapEditor.class, new int[]{
                    popupDoor.destX * 8, popupDoor.destY * 8});
        }
    }

    // Sprites
    private MapData.SpriteEntry getSpriteEntryFromMouseXY(int mouseX,
                                                          int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        return map.getSpriteEntryFromCoords(areaX, areaY, mouseX, mouseY);
    }

    private int popNpcIdFromMouseXY(int mouseX, int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        return map.popNPCFromCoords(areaX, areaY, mouseX, mouseY);
    }

    private void pushNpcIdFromMouseXY(int npc, int mouseX, int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        map.pushNPCFromCoords(npc, areaX, areaY, mouseX, mouseY);
    }

    private void pushNpcIdFromMapPixelXY(int npc, int mapPixelX, int mapPixelY) {
        final int areaX = (mapPixelX / MapData.TILE_WIDTH) / 8;
        final int areaY = (mapPixelY / MapData.TILE_HEIGHT) / 8;
        mapPixelX %= (MapData.TILE_WIDTH * 8);
        mapPixelY %= (MapData.TILE_HEIGHT * 8);
        map.pushNPCFromCoords(npc, areaX, areaY, mapPixelX, mapPixelY);
    }

    // Doors
    private MapData.Door getDoorFromMouseXY(int mouseX, int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        return map.getDoorFromCoords(areaX, areaY, mouseX / 8, mouseY / 8);
    }

    private MapData.Door popDoorFromMouseXY(int mouseX, int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        return map.popDoorFromCoords(areaX, areaY, mouseX / 8, mouseY / 8);
    }

    private void pushDoorFromMouseXY(MapData.Door door, int mouseX,
                                     int mouseY) {
        int areaX = (x + mouseX / MapData.TILE_WIDTH) / 8, areaY = (y + mouseY
                / MapData.TILE_HEIGHT) / 8;
        mouseX += (x % 8) * MapData.TILE_WIDTH;
        mouseX %= (MapData.TILE_WIDTH * 8);
        mouseY += (y % 8) * MapData.TILE_HEIGHT;
        mouseY %= (MapData.TILE_HEIGHT * 8);
        door.x = mouseX / 8;
        door.y = mouseY / 8;
        map.pushDoorFromCoords(door, areaX, areaY);
    }

    private static final Cursor blankCursor = Toolkit.getDefaultToolkit()
            .createCustomCursor(
                    new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                    new Point(0, 0), "blank cursor");

    public void mousePressed(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (e.isControlDown() && (e.getButton() == MouseEvent.BUTTON1)) {
            if (previousMode == null) {
                previousMode = currentMode;
                changeMode(MapMode.PREVIEW);

                tvPreview = true;
                tvPreviewX = e.getX();
                tvPreviewY = e.getY();

                tvPreviewH = 224 / 2;
                if (prefs.getValueAsBoolean("maskOverscan")) {
                    tvPreviewW = 240 / 2;
                } else {
                    tvPreviewW = 256 / 2;
                }

                this.setCursor(blankCursor);
                repaint();
            }
        } else if (e.getButton() == MouseEvent.BUTTON1) {
            if (currentMode == MapMode.SPRITE && (movingNPC == -1)) {
                movingNPC = popNpcIdFromMouseXY(mx, my);
                if (movingNPC != -1) {
                    MapData.NPC tmp = map.getNPC(movingNPC);
                    movingNPCimg = map.getSpriteImage(tmp.sprite,
                            tmp.direction);
                    movingNPCdim = map.getSpriteWH(tmp.sprite);
                    movingDrawX = mx - movingNPCdim[0] / 2 + 1;
                    movingDrawY = my - movingNPCdim[1] + 9;
                    repaint();
                }
            } else if (currentMode == MapMode.DOOR && (movingDoor == null)) {
                movingDoor = popDoorFromMouseXY(mx, my);
                if (movingDoor != null) {
                    movingDrawX = mx & (~7);
                    movingDrawY = my & (~7);
                    repaint();
                }
            }
        }
    }

    public void mouseReleased(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (e.getButton() == 1) {
            if (previousMode != null) {
                changeMode(previousMode);
                previousMode = null;
                this.setCursor(Cursor.getDefaultCursor());
                tvPreview = false;
                repaint();
            } else if (currentMode == MapMode.SPRITE && (movingNPC != -1)) {
                pushNpcIdFromMouseXY(movingNPC, mx, my);
                movingNPC = -1;
                repaint();
            } else if (currentMode == MapMode.DOOR && (movingDoor != null)) {
                pushDoorFromMouseXY(movingDoor, mx, my);
                movingDoor = null;
                repaint();
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseExited(MouseEvent e) {
        pixelCoordLabel.setText("Pixel X,Y: (-,-)");
        warpCoordLabel.setText("Warp X,Y: (-,-)");
        tileCoordLabel.setText("Tile X,Y: (-,-)");
    }

    public void mouseDragged(MouseEvent e) {
        if (tvPreview) {
            tvPreviewX = e.getX();
            tvPreviewY = e.getY();
            repaint();
        } else if (movingNPC != -1) {
            movingDrawX = e.getX() - movingNPCdim[0] / 2 + 1;
            movingDrawY = e.getY() - movingNPCdim[1] + 9;
            repaint();
        } else if (movingDoor != null) {
            movingDrawX = e.getX() & (~7);
            movingDrawY = e.getY() & (~7);
            repaint();
        }

        updateCoordLabels(e.getX(), e.getY());
    }

    public void mouseMoved(MouseEvent e) {
        if (currentMode == MapMode.SEEK_DOOR) {
            seekDrawX = e.getX() & (~7);
            seekDrawY = e.getY() & (~7);
            repaint();
        } else if (currentMode == MapMode.HOTSPOT && (editHS != null)) {
            hsMouseX = e.getX() & (~7);
            hsMouseY = e.getY() & (~7);
            repaint();
        }

        updateCoordLabels(e.getX(), e.getY());
    }

    private void updateCoordLabels(final int mouseX, final int mouseY) {
        if ((mouseX >= 0) && (mouseY >= 0)) {
            pixelCoordLabel.setText("Pixel X,Y: ("
                    + (x * MapData.TILE_WIDTH + mouseX - 1) + ","
                    + (y * MapData.TILE_WIDTH + mouseY - 1) + ")");
            warpCoordLabel.setText("Warp X,Y: ("
                    + (x * 4 + (mouseX - 1) / 8) + ","
                    + (y * 4 + (mouseY - 1) / 8) + ")");
            tileCoordLabel.setText("Tile X,Y: ("
                    + (x + (mouseX - 1) / MapData.TILE_WIDTH) + ","
                    + (y + (mouseY - 1) / MapData.TILE_HEIGHT) + ")");
        }
    }

    public void changeMode(MapMode mode) {
        gamePreview = mode == MapMode.PREVIEW;

        if (mode == MapMode.MAP) {
            undoButton.setEnabled(!undoStack.isEmpty());
            redoButton.setEnabled(!redoStack.isEmpty());
            copySector.setEnabled(selectedSector != null);
            pasteSector.setEnabled(selectedSector != null);
            copySector2.setEnabled(selectedSector != null);
            pasteSector2.setEnabled(selectedSector != null);
        } else {
            undoButton.setEnabled(false);
            redoButton.setEnabled(false);
            copySector.setEnabled(false);
            pasteSector.setEnabled(false);
            copySector2.setEnabled(false);
            pasteSector2.setEnabled(false);
        }

        currentMode = mode;
    }

    public void seek(DoorEditor de) {
        changeMode(MapMode.SEEK_DOOR);
        doorSeeker = de;
    }

    public void toggleGrid() {
        grid = !grid;
    }

    public void toggleSpriteBoxes() {
        spriteBoxes = !spriteBoxes;
    }

    public void toggleTileNums() {
        drawTileNums = !drawTileNums;
    }

    public void toggleSpriteNums() {
        drawSpriteNums = !drawSpriteNums;
    }

    public void toggleMapChanges() {
        // TODO Auto-generated method stub

    }

    public boolean undoMapAction() {
        if (!undoStack.empty()) {
            Object undo = undoStack.pop();
            if (undo instanceof UndoableTileChange) {
                UndoableTileChange tc = (UndoableTileChange) undo;
                map.setMapTile(tc.x, tc.y, tc.oldTile);
            } else if (undo instanceof UndoableSectorPaste) {
                // UndoableSectorPaste usp = (UndoableSectorPaste) undo;
                // TODO
            }
            if (undoStack.isEmpty())
                undoButton.setEnabled(false);
            redoStack.push(undo);
            redoButton.setEnabled(true);
            repaint();
            return true;
        } else
            return false;
    }

    public boolean redoMapAction() {
        if (!redoStack.empty()) {
            Object redo = redoStack.pop();
            if (redo instanceof UndoableTileChange) {
                UndoableTileChange tc = (UndoableTileChange) redo;
                map.setMapTile(tc.x, tc.y, tc.newTile);
            } else if (redo instanceof UndoableSectorPaste) {
                // TODO
            }
            if (redoStack.isEmpty())
                redoButton.setEnabled(false);
            undoStack.push(redo);
            undoButton.setEnabled(true);
            repaint();
            return true;
        } else
            return false;
    }

    public void setScreenSize(int newSW, int newSH) {
        if ((newSW != screenWidth) || (newSH != screenHeight)) {
            screenWidth = newSW;
            screenHeight = newSH;

            setMapXY(x, y);

            setPreferredSize(new Dimension(screenWidth * MapData.TILE_WIDTH
                    + 2, screenHeight * MapData.TILE_HEIGHT + 2));

            repaint();
        }
    }

    public void pasteSector(MapData.Sector copiedSector, int sectorX2,
                            int sectorY2, int[][] copiedSectorTiles) {
        for (int i = 0; i < copiedSectorTiles.length; i++)
            for (int j = 0; j < copiedSectorTiles[i].length; j++) {
                map.setMapTile(sectorX * 8 + j, sectorY * 4 + i,
                        copiedSectorTiles[i][j]);
            }
        map.getSector(sectorX, sectorY).copy(copiedSector);
        // TODO
        // undoStack.push(new UndoableSectorPaste(sectorX, sectorY,
        // copiedSectorTiles, copiedSector));
    }
}