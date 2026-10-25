package mini.mario;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

final class GamePanel extends JPanel {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 576;
    private static final int TILE = 48;
    private static final int ROWS = 12;
    private static final int COLS = 112;
    private static final int GROUND_Y = 10 * TILE;
    private static final int PLAYER_W = 32;
    private static final int PLAYER_H = 42;
    private static final int EMPTY = 0;
    private static final int GROUND = 1;
    private static final int BRICK = 2;
    private static final int QUESTION = 3;
    private static final int USED = 4;
    private static final int PIPE = 5;

    private static final Color SKY = new Color(106, 190, 245);
    private static final Color INK = new Color(47, 42, 52);
    private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.BOLD, 19);
    private static final Font TITLE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 52);
    private static final Font BODY_FONT = new Font(Font.MONOSPACED, Font.BOLD, 20);

    private enum State { TITLE, PLAYING, PAUSED, WON, GAME_OVER }

    private final int[][] tiles = new int[ROWS][COLS];
    private final List<Coin> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final Timer timer;
    private State state = State.TITLE;
    private double playerX;
    private double playerY;
    private double velocityX;
    private double velocityY;
    private double cameraX;
    private boolean left;
    private boolean right;
    private boolean jumpHeld;
    private boolean jumpQueued;
    private boolean onGround;
    private boolean facingRight = true;
    private int score;
    private int coinCount;
    private int lives;
    private int timeLeft;
    private int ticks;
    private int invincibleTicks;

    GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(SKY);
        setDoubleBuffered(true);
        bindKeys();
        resetGame();
        this.state = State.TITLE;
        this.timer = new Timer(16, e -> tick());
        this.timer.start();
    }

    private void bindKeys() {
        bind("pressed LEFT", "leftDown", () -> this.left = true);
        bind("released LEFT", "leftUp", () -> this.left = false);
        bind("pressed A", "aDown", () -> this.left = true);
        bind("released A", "aUp", () -> this.left = false);
        bind("pressed RIGHT", "rightDown", () -> this.right = true);
        bind("released RIGHT", "rightUp", () -> this.right = false);
        bind("pressed D", "dDown", () -> this.right = true);
        bind("released D", "dUp", () -> this.right = false);
        bind("pressed SPACE", "spaceDown", this::pressJump);
        bind("released SPACE", "spaceUp", () -> this.jumpHeld = false);
        bind("pressed UP", "upDown", this::pressJump);
        bind("released UP", "upUp", () -> this.jumpHeld = false);
        bind("pressed W", "wDown", this::pressJump);
        bind("released W", "wUp", () -> this.jumpHeld = false);
        bind("pressed P", "pause", () -> {
            if (this.state == State.PLAYING) this.state = State.PAUSED;
            else if (this.state == State.PAUSED) this.state = State.PLAYING;
        });
        bind("pressed R", "restart", () -> {
            resetGame();
            this.state = State.PLAYING;
        });
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void pressJump() {
        if (this.state == State.TITLE || this.state == State.WON || this.state == State.GAME_OVER) {
            resetGame();
            this.state = State.PLAYING;
        } else if (this.state == State.PLAYING && !this.jumpHeld) {
            this.jumpQueued = true;
        }
        this.jumpHeld = true;
    }

    private void resetGame() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) this.tiles[row][col] = EMPTY;
        }
        this.coins.clear();
        this.enemies.clear();
        this.sparks.clear();
        buildLevel();
        this.playerX = 2 * TILE;
        this.playerY = GROUND_Y - PLAYER_H;
        this.velocityX = 0;
        this.velocityY = 0;
        this.cameraX = 0;
        this.left = false;
        this.right = false;
        this.jumpHeld = false;
        this.jumpQueued = false;
        this.onGround = true;
        this.facingRight = true;
        this.score = 0;
        this.coinCount = 0;
        this.lives = 3;
        this.timeLeft = 300;
        this.ticks = 0;
        this.invincibleTicks = 0;
    }

    private void buildLevel() {
        for (int col = 0; col < COLS; col++) {
            if (inRange(col, 26, 28) || inRange(col, 52, 54) || inRange(col, 78, 80)) continue;
            this.tiles[10][col] = GROUND;
            this.tiles[11][col] = GROUND;
        }
        blocks(7, 8, BRICK, QUESTION, BRICK, QUESTION, BRICK);
        blocks(7, 22, BRICK, QUESTION, BRICK);
        blocks(6, 32, BRICK, BRICK, QUESTION, BRICK, BRICK);
        blocks(7, 45, QUESTION, BRICK, QUESTION);
        blocks(7, 59, BRICK, QUESTION, BRICK, BRICK);
        blocks(6, 70, BRICK, QUESTION, BRICK, QUESTION);
        blocks(7, 86, QUESTION, BRICK, QUESTION);
        blocks(6, 98, BRICK, BRICK, QUESTION, BRICK);
        pipe(17, 2);
        pipe(39, 3);
        pipe(66, 2);
        pipe(91, 3);

        coinLine(6, 9, 4);
        coinLine(21, 6, 3);
        coinLine(26, 7, 3);
        coinLine(33, 7, 4);
        coinLine(51, 7, 4);
        coinLine(59, 6, 4);
        coinLine(78, 7, 3);
        coinLine(85, 6, 4);
        coinLine(99, 7, 3);

        enemy(13, 12, 16);
        enemy(23, 21, 25);
        enemy(36, 32, 38);
        enemy(47, 43, 51);
        enemy(62, 58, 65);
        enemy(73, 69, 77);
        enemy(87, 82, 90);
        enemy(101, 96, 105);
    }

    private static boolean inRange(int value, int first, int last) {
        return value >= first && value <= last;
    }

    private void blocks(int row, int col, int... kinds) {
        for (int i = 0; i < kinds.length; i++) this.tiles[row][col + i] = kinds[i];
    }

    private void pipe(int col, int height) {
        for (int row = 10 - height; row < 10; row++) {
            this.tiles[row][col] = PIPE;
            this.tiles[row][col + 1] = PIPE;
        }
    }

    private void coinLine(int col, int row, int count) {
        for (int i = 0; i < count; i++) this.coins.add(new Coin((col + i) * TILE + TILE / 2.0, row * TILE + 10));
    }

    private void enemy(int col, int minCol, int maxCol) {
        this.enemies.add(new Enemy(col * TILE + 8, minCol * TILE, maxCol * TILE + 10));
    }

    private void tick() {
        this.ticks++;
        if (this.state == State.PLAYING) {
            if (this.ticks % 62 == 0 && --this.timeLeft <= 0) loseLife();
            if (this.state == State.PLAYING) {
                updatePlayer();
                updateCoins();
                updateEnemies();
                updateSparks();
                if (this.invincibleTicks > 0) this.invincibleTicks--;
                this.cameraX = clamp(this.playerX - WIDTH * 0.37, 0, COLS * TILE - WIDTH);
                if (this.playerX + PLAYER_W >= (COLS - 3) * TILE) this.state = State.WON;
            }
        }
        repaint();
    }

    private void updatePlayer() {
        double target = (this.right ? 4.5 : 0) - (this.left ? 4.5 : 0);
        this.velocityX += (target - this.velocityX) * (this.onGround ? 0.24 : 0.15);
        if (Math.abs(this.velocityX) < 0.08) this.velocityX = 0;
        if (this.velocityX != 0) this.facingRight = this.velocityX > 0;
        if (this.jumpQueued && this.onGround) {
            this.velocityY = -15.0;
            this.onGround = false;
        }
        this.jumpQueued = false;
        if (!this.jumpHeld && this.velocityY < -5) this.velocityY += 0.35;
        this.velocityY = Math.min(this.velocityY + 0.68, 15);

        this.playerX += this.velocityX;
        resolveHorizontal();
        this.playerX = clamp(this.playerX, 0, COLS * TILE - PLAYER_W);
        this.playerY += this.velocityY;
        this.onGround = false;
        resolveVertical();
        if (this.playerY > HEIGHT + 100) loseLife();
    }

    private void resolveHorizontal() {
        int firstRow = Math.max(0, (int) Math.floor(this.playerY / TILE));
        int lastRow = Math.min(ROWS - 1, (int) Math.floor((this.playerY + PLAYER_H - 1) / TILE));
        int firstCol = Math.max(0, (int) Math.floor(this.playerX / TILE));
        int lastCol = Math.min(COLS - 1, (int) Math.floor((this.playerX + PLAYER_W - 1) / TILE));
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                if (this.tiles[row][col] == EMPTY) continue;
                if (this.velocityX > 0 && this.playerX + PLAYER_W > col * TILE) {
                    this.playerX = col * TILE - PLAYER_W;
                    this.velocityX = 0;
                } else if (this.velocityX < 0 && this.playerX < (col + 1) * TILE) {
                    this.playerX = (col + 1) * TILE;
                    this.velocityX = 0;
                }
            }
        }
    }

    private void resolveVertical() {
        int firstRow = Math.max(0, (int) Math.floor(this.playerY / TILE));
        int lastRow = Math.min(ROWS - 1, (int) Math.floor((this.playerY + PLAYER_H - 1) / TILE));
        int firstCol = Math.max(0, (int) Math.floor(this.playerX / TILE));
        int lastCol = Math.min(COLS - 1, (int) Math.floor((this.playerX + PLAYER_W - 1) / TILE));
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                if (this.tiles[row][col] == EMPTY) continue;
                if (this.velocityY > 0) {
                    this.playerY = row * TILE - PLAYER_H;
                    this.velocityY = 0;
                    this.onGround = true;
                } else if (this.velocityY < 0) {
                    this.playerY = (row + 1) * TILE;
                    this.velocityY = 0;
                    hitBlock(row, col);
                }
            }
        }
    }

    private void hitBlock(int row, int col) {
        if (this.tiles[row][col] == QUESTION) {
            this.tiles[row][col] = USED;
            this.coinCount++;
            this.score += 100;
            burst(col * TILE + TILE / 2.0, row * TILE, new Color(255, 222, 58));
        } else if (this.tiles[row][col] == BRICK) {
            this.score += 10;
            burst(col * TILE + TILE / 2.0, row * TILE, new Color(186, 99, 62));
        }
    }

    private void updateCoins() {
        Iterator<Coin> iterator = this.coins.iterator();
        while (iterator.hasNext()) {
            Coin coin = iterator.next();
            if (this.playerX < coin.x + 12 && this.playerX + PLAYER_W > coin.x - 12
                    && this.playerY < coin.y + 14 && this.playerY + PLAYER_H > coin.y - 14) {
                iterator.remove();
                this.coinCount++;
                this.score += 100;
                burst(coin.x, coin.y, new Color(255, 226, 68));
            }
        }
    }

    private void updateEnemies() {
        for (Enemy enemy : this.enemies) {
            if (!enemy.alive) continue;
            enemy.x += enemy.speed;
            if (enemy.x < enemy.min || enemy.x > enemy.max) {
                enemy.speed = -enemy.speed;
                enemy.x = clamp(enemy.x, enemy.min, enemy.max);
            }
            if (this.playerX + PLAYER_W - 5 > enemy.x && this.playerX + 5 < enemy.x + 32
                    && this.playerY + PLAYER_H > enemy.y + 3 && this.playerY < enemy.y + 30) {
                if (this.velocityY > 0 && this.playerY + PLAYER_H - this.velocityY <= enemy.y + 14) {
                    enemy.alive = false;
                    this.velocityY = -9.5;
                    this.score += 200;
                    burst(enemy.x + 16, enemy.y + 10, new Color(220, 120, 69));
                } else if (this.invincibleTicks == 0) {
                    loseLife();
                    return;
                }
            }
        }
    }

    private void loseLife() {
        this.lives--;
        if (this.lives <= 0 || this.timeLeft <= 0) {
            this.state = State.GAME_OVER;
            return;
        }
        this.playerX = 2 * TILE;
        this.playerY = GROUND_Y - PLAYER_H;
        this.velocityX = 0;
        this.velocityY = 0;
        this.onGround = true;
        this.invincibleTicks = 120;
        this.cameraX = 0;
    }

    private void burst(double x, double y, Color color) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            this.sparks.add(new Spark(x, y, Math.cos(angle) * 3, Math.sin(angle) * 3 - 2, color));
        }
    }

    private void updateSparks() {
        this.sparks.removeIf(spark -> --spark.life <= 0);
        for (Spark spark : this.sparks) {
            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.vy += 0.25;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawBackground(g);
        g.translate(-Math.round(this.cameraX), 0);
        drawWorld(g);
        g.translate(Math.round(this.cameraX), 0);
        drawHud(g);
        if (this.state != State.PLAYING) drawOverlay(g);
        g.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(SKY);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(255, 250, 205));
        g.fillOval(720, 80, 84, 84);
        for (int i = -1; i < 8; i++) {
            int x = (int) (i * 280 - this.cameraX * 0.22 % 280);
            drawCloud(g, x + 35, 100 + (i % 3) * 44);
        }
        for (int i = -1; i < 9; i++) {
            int x = (int) (i * 230 - this.cameraX * 0.38 % 230);
            g.setColor(new Color(98, 204, 143));
            g.fillOval(x, 355, 240, 230);
            g.setColor(new Color(77, 184, 129));
            g.fillOval(x + 120, 400, 160, 150);
            g.setColor(new Color(47, 139, 103));
            g.fillOval(x + 76, 406, 8, 13);
            g.fillOval(x + 102, 406, 8, 13);
        }
    }

    private void drawCloud(Graphics2D g, int x, int y) {
        g.setColor(new Color(234, 249, 255));
        g.fillOval(x, y + 18, 105, 38);
        g.fillOval(x + 16, y, 55, 56);
        g.fillOval(x + 52, y + 7, 50, 46);
        g.setColor(Color.WHITE);
        g.fillOval(x + 13, y + 17, 77, 33);
    }

    private void drawWorld(Graphics2D g) {
        int first = Math.max(0, (int) this.cameraX / TILE - 1);
        int last = Math.min(COLS - 1, (int) (this.cameraX + WIDTH) / TILE + 1);
        for (int row = 0; row < ROWS; row++) {
            for (int col = first; col <= last; col++) {
                if (this.tiles[row][col] != EMPTY) drawTile(g, row, col);
            }
        }
        for (Coin coin : this.coins) {
            if (coin.x < this.cameraX - 20 || coin.x > this.cameraX + WIDTH + 20) continue;
            int x = (int) coin.x;
            int y = (int) coin.y + (int) (Math.sin(this.ticks * 0.11 + coin.x) * 3);
            g.setColor(new Color(172, 107, 27));
            g.fillOval(x - 11, y - 15, 22, 30);
            g.setColor(new Color(255, 214, 53));
            g.fillOval(x - 9, y - 14, 18, 27);
            g.setColor(new Color(255, 247, 162));
            g.fillRect(x - 4, y - 9, 4, 13);
        }
        drawFlag(g);
        for (Enemy enemy : this.enemies) if (enemy.alive) drawEnemy(g, enemy);
        if (this.invincibleTicks == 0 || this.ticks % 8 < 4) drawPlayer(g);
        for (Spark spark : this.sparks) {
            g.setColor(spark.color);
            g.fillRect((int) spark.x, (int) spark.y, 5, 5);
        }
    }

    private void drawTile(Graphics2D g, int row, int col) {
        int x = col * TILE;
        int y = row * TILE;
        int tile = this.tiles[row][col];
        if (tile == GROUND) {
            g.setColor(new Color(163, 89, 50));
            g.fillRect(x, y, TILE, TILE);
            g.setColor(new Color(105, 65, 51));
            g.drawRect(x, y, TILE - 1, TILE - 1);
            g.fillRect(x + 23, y + 2, 3, 17);
            g.fillRect(x + 8, y + 24, 3, 23);
            g.fillRect(x + 33, y + 24, 3, 23);
            g.fillRect(x + 1, y + 22, 45, 3);
            if (row == 10) {
                g.setColor(new Color(93, 185, 81));
                g.fillRect(x, y, TILE, 7);
                g.setColor(new Color(154, 222, 91));
                g.fillRect(x, y, TILE, 3);
            }
        } else if (tile == PIPE) {
            g.setColor(new Color(32, 130, 67));
            g.fillRect(x, y, TILE, TILE);
            g.setColor(new Color(60, 198, 91));
            g.fillRect(x + 7, y, 15, TILE);
            g.setColor(new Color(22, 98, 55));
            g.fillRect(x + 38, y, 8, TILE);
            if (row == 8 || row == 7) {
                if (row == 0 || this.tiles[row - 1][col] != PIPE) {
                    g.setColor(new Color(28, 110, 62));
                    g.fillRect(x - (col > 0 && this.tiles[row][col - 1] == PIPE ? 0 : 4), y, 52, 12);
                    g.setColor(new Color(82, 222, 105));
                    g.fillRect(x + 4, y + 2, 19, 6);
                }
            }
        } else {
            g.setColor(tile == QUESTION ? new Color(245, 166, 45)
                    : tile == USED ? new Color(151, 137, 125) : new Color(194, 105, 62));
            g.fillRect(x + 1, y + 1, 46, 46);
            g.setColor(tile == QUESTION ? new Color(255, 221, 90)
                    : tile == USED ? new Color(184, 174, 159) : new Color(229, 148, 89));
            g.fillRect(x + 3, y + 3, 42, 5);
            g.setColor(new Color(108, 72, 59));
            g.drawRect(x + 1, y + 1, 45, 45);
            if (tile == QUESTION) {
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 36));
                g.setColor(Color.WHITE);
                g.drawString("?", x + 14, y + 36);
            } else if (tile == BRICK) {
                g.fillRect(x + 1, y + 22, 46, 3);
                g.fillRect(x + 22, y + 1, 3, 21);
                g.fillRect(x + 10, y + 25, 3, 22);
                g.fillRect(x + 35, y + 25, 3, 22);
            } else {
                g.fillRect(x + 21, y + 21, 6, 6);
            }
        }
    }

    private void drawFlag(Graphics2D g) {
        int x = (COLS - 3) * TILE + 20;
        g.setColor(new Color(234, 240, 232));
        g.fillRect(x, 143, 7, GROUND_Y - 143);
        g.setColor(new Color(230, 194, 50));
        g.fillOval(x - 6, 132, 19, 19);
        g.setColor(new Color(59, 187, 97));
        int[] xs = {x + 7, x + 75, x + 7};
        int[] ys = {162, 181, 201};
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(220, 225, 213));
        g.fillRect(x - 19, GROUND_Y - 8, 45, 8);
    }

    private void drawPlayer(Graphics2D g) {
        int x = (int) Math.round(this.playerX);
        int y = (int) Math.round(this.playerY);
        g.setColor(INK);
        g.fillRect(x + 6, y + 7, 22, 34);
        g.setColor(new Color(222, 55, 53));
        g.fillRect(x + 5, y + 3, 24, 8);
        g.fillRect(x + 2, y + 10, 29, 5);
        g.fillRect(x + 7, y + 23, 19, 9);
        g.fillRect(x + 3, y + 24, 7, 12);
        g.fillRect(x + 24, y + 24, 7, 12);
        g.setColor(new Color(246, 187, 135));
        g.fillRect(x + 9, y + 14, 19, 11);
        g.fillRect(x + (this.facingRight ? 25 : 4), y + 18, 5, 6);
        g.setColor(INK);
        g.fillRect(x + (this.facingRight ? 22 : 12), y + 15, 3, 5);
        g.fillRect(x + (this.facingRight ? 17 : 9), y + 21, 9, 3);
        g.setColor(new Color(48, 103, 194));
        g.fillRect(x + 9, y + 29, 16, 9);
        g.fillRect(x + 9, y + 24, 5, 10);
        g.fillRect(x + 20, y + 24, 5, 10);
        g.setColor(new Color(255, 225, 111));
        g.fillRect(x + 10, y + 29, 3, 3);
        g.fillRect(x + 21, y + 29, 3, 3);
        g.setColor(new Color(108, 61, 48));
        g.fillRect(x + 4, y + 37, 11, 5);
        g.fillRect(x + 19, y + 37, 11, 5);
    }

    private void drawEnemy(Graphics2D g, Enemy enemy) {
        int x = (int) enemy.x;
        int y = (int) enemy.y;
        g.setColor(new Color(111, 58, 49));
        g.fillOval(x, y + 5, 32, 25);
        g.setColor(new Color(174, 104, 63));
        g.fillOval(x + 2, y, 28, 25);
        g.setColor(new Color(246, 216, 172));
        g.fillRect(x + 5, y + 18, 22, 9);
        g.setColor(Color.WHITE);
        g.fillRect(x + 8, y + 13, 6, 8);
        g.fillRect(x + 19, y + 13, 6, 8);
        g.setColor(INK);
        g.fillRect(x + 10, y + 16, 3, 5);
        g.fillRect(x + 20, y + 16, 3, 5);
        g.fillRect(x + 3, y + 27, 10, 5);
        g.fillRect(x + 20, y + 27, 10, 5);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(30, 48, 67, 190));
        g.fillRoundRect(15, 13, WIDTH - 30, 44, 15, 15);
        g.setColor(Color.WHITE);
        g.setFont(HUD_FONT);
        g.drawString("MARIO  " + String.format("%06d", this.score), 30, 42);
        g.drawString("COINS  " + String.format("%02d", this.coinCount), 295, 42);
        g.drawString("LIVES  " + this.lives, 540, 42);
        g.drawString("TIME  " + String.format("%03d", this.timeLeft), 740, 42);
    }

    private void drawOverlay(Graphics2D g) {
        g.setColor(new Color(16, 31, 52, 185));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        String title;
        String subtitle;
        if (this.state == State.TITLE) {
            title = "MINI MARIO";
            subtitle = "收集金币，踩掉敌人，冲向旗帜！";
        } else if (this.state == State.PAUSED) {
            title = "已暂停";
            subtitle = "按 P 继续游戏";
        } else if (this.state == State.WON) {
            title = "过关成功！";
            subtitle = "最终得分  " + this.score;
        } else {
            title = "游戏结束";
            subtitle = "最终得分  " + this.score;
        }
        g.setFont(TITLE_FONT);
        drawCentered(g, title, 236, new Color(255, 225, 90));
        g.setFont(BODY_FONT);
        drawCentered(g, subtitle, 291, Color.WHITE);
        String hint = this.state == State.PAUSED ? "方向键 / A D 移动     空格跳跃"
                : "按空格开始     方向键 / A D 移动     空格跳跃";
        drawCentered(g, hint, 345, new Color(220, 239, 255));
        if (this.state != State.TITLE) drawCentered(g, "R 重新开始", 382, new Color(220, 239, 255));
    }

    private void drawCentered(Graphics2D g, String value, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(color);
        g.drawString(value, (WIDTH - metrics.stringWidth(value)) / 2, y);
    }

    private static final class Coin {
        final double x;
        final double y;
        Coin(double x, double y) { this.x = x; this.y = y; }
    }

    private static final class Enemy {
        double x;
        final double y = GROUND_Y - 32;
        final double min;
        final double max;
        double speed = -1.2;
        boolean alive = true;
        Enemy(double x, double min, double max) {
            this.x = x;
            this.min = min;
            this.max = max;
        }
    }

    private static final class Spark {
        double x;
        double y;
        final double vx;
        double vy;
        final Color color;
        int life = 22;
        Spark(double x, double y, double vx, double vy, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
    }
}
