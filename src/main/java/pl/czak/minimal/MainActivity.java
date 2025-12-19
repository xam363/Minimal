package pl.czak.minimal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new PongView(this));
    }

    class PongView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ArrayList<Ball> balls = new ArrayList<>();
        private ArrayList<Particle> particles = new ArrayList<>();
        private ArrayList<Obstacle> obstacles = new ArrayList<>();
        private ArrayList<Obstacle> pendingObs = new ArrayList<>();
        private ArrayList<Star> stars = new ArrayList<>();
        private ArrayList<FloatingText> texts = new ArrayList<>();
        
        private float paddleX = 0, paddleWidth = 250, paddleHeight = 45;
        private int score = 0, highScore = 0, combo = 0, hitsToSplit = 0;
        private float shakeIntensity = 0;
        private int flashAlpha = 0;
        private long bonusEndTime = 0, nextBonusTime = 0;
        private Random random = new Random();
        private SharedPreferences prefs;

        public PongView(Context context) {
            super(context);
            // Используем стабильное имя файла настроек
            prefs = context.getSharedPreferences("CyberPongPrefs", Context.MODE_PRIVATE);
            highScore = prefs.getInt("highScore", 0);
            
            balls.add(new Ball(300, 400, 15, 15));
            for (int i = 0; i < 80; i++) stars.add(new Star());
        }

        private String getRank() {
            if (combo > 50) return "SSS"; if (combo > 30) return "SS";
            if (combo > 20) return "S"; if (combo > 10) return "A";
            if (combo > 5) return "B"; return "C";
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now = System.currentTimeMillis();
            boolean isHyper = now < bonusEndTime;
            float paddleY = getHeight() - (getHeight() * 0.15f);
            float speedFactor = Math.min(2.8f, (1.0f + (score / 5000f)) * (isHyper ? 1.4f : 1.0f));

            if (shakeIntensity > 1) {
                canvas.translate((random.nextFloat() - 0.5f) * shakeIntensity, (random.nextFloat() - 0.5f) * shakeIntensity);
                shakeIntensity *= 0.88f;
            }

            canvas.drawColor(isHyper ? Color.rgb(20, 0, 30) : Color.rgb(5, 5, 15));

            for (Star s : stars) {
                s.update(speedFactor);
                paint.setColor(Color.WHITE);
                paint.setAlpha(s.alpha);
                canvas.drawCircle(s.x, s.y, s.size, paint);
            }

            if (random.nextInt(100) < (4 + balls.size())) {
                int t = random.nextInt(25);
                boolean s = (t == 0), f = (t > 0 && t < 4), sp = (t == 5), 
                        b = (t == 6 && now > nextBonusTime && !isHyper), st = (t > 20);
                obstacles.add(new Obstacle(random.nextInt(getWidth()), -100, s, f, sp, false, b, st));
            }

            float pW = isHyper ? getWidth() : paddleWidth;
            float pX = isHyper ? 0 : paddleX;

            for (int i = balls.size() - 1; i >= 0; i--) {
                Ball ball = balls.get(i);
                ball.update(speedFactor, getWidth());
                particles.add(new Particle(ball.x, ball.y, isHyper ? Color.CYAN : Color.WHITE, 12, 0));

                if (ball.y >= paddleY - 40 && ball.y <= paddleY + 20 && ball.x >= pX - 30 && ball.x <= pX + pW + 30) {
                    ball.y = paddleY - 42;
                    ball.speedY = -Math.abs(ball.speedY);
                    ball.speedX += (random.nextFloat() - 0.5f) * 18;
                    hitsToSplit++;
                    shakeIntensity = 10;
                    if (hitsToSplit >= 3) { spawnExtraBalls(ball.x, ball.y); hitsToSplit = 0; }
                }
                if (ball.y > getHeight() + 100) balls.remove(i);
                else ball.draw(canvas, paint, isHyper);
            }
            if (balls.isEmpty()) resetGame();

            Iterator<Obstacle> oIter = obstacles.iterator();
            while (oIter.hasNext()) {
                Obstacle o = oIter.next();
                o.y += (o.isStone ? 5 : (o.isFast ? 16 : 8)) * speedFactor;
                if (o.isFragment) o.x += o.vx * speedFactor;

                boolean hit = false;
                for (Ball b : balls) {
                    if (Math.hypot(b.x - o.x, b.y - o.y) < 90) {
                        b.speedY = -b.speedY;
                        if (o.isStone) { shakeIntensity = 12; break; }
                        o.hp--;
                        if (o.hp > 0) { 
                            o.scale = 1.8f; shakeIntensity = 25; 
                            flashAlpha = 50; 
                        } else {
                            if (o.isBonus) { bonusEndTime = now + 6000; nextBonusTime = bonusEndTime + 4000; flashAlpha = 150; }
                            if (o.isSplitter) for(int k=0; k<4; k++){ Obstacle f=new Obstacle(o.x,o.y,false,false,false,true,false,false); f.vx=(k-1.5f)*10; pendingObs.add(f); }
                            
                            combo++;
                            int p = (o.isSuper ? 300 : (o.isBonus ? 500 : 40)) * (1 + combo/5);
                            score += p;
                            texts.add(new FloatingText(o.x, o.y, "+" + p, o.getColor()));
                            explode(o.x, o.y, o.getColor(), 30);
                            shakeIntensity = o.isSuper ? 80 : 35;
                            flashAlpha = o.isSuper ? 100 : 30;
                            hit = true; 
                            try { Thread.sleep(isHyper || o.isSuper ? 40 : 10); } catch(Exception e){}
                            break;
                        }
                    }
                }
                if (hit || o.y > getHeight() + 200) { if (o.y > getHeight() && !o.isStone) combo = 0; oIter.remove(); }
                else o.draw(canvas, paint);
            }
            obstacles.addAll(pendingObs); pendingObs.clear();

            for (int i = particles.size()-1; i>=0; i--) if(!particles.get(i).update()) particles.remove(i); else particles.get(i).draw(canvas, paint);
            for (int i = texts.size()-1; i>=0; i--) if(!texts.get(i).update()) texts.remove(i); else texts.get(i).draw(canvas, paint);

            paint.setShader(new LinearGradient(pX, 0, pX+pW, 0, isHyper ? Color.GREEN : Color.CYAN, Color.BLUE, Shader.TileMode.CLAMP));
            canvas.drawRect(pX, paddleY, pX + pW, paddleY + paddleHeight, paint);
            paint.setShader(null);

            // ИНТЕРФЕЙС
            paint.setColor(Color.WHITE); paint.setTextSize(60);
            canvas.drawText("SCORE: " + score, 50, 100, paint);
            
            paint.setColor(Color.LTGRAY); paint.setTextSize(40);
            String bestStr = "BEST: " + highScore;
            canvas.drawText(bestStr, getWidth() - paint.measureText(bestStr) - 50, 100, paint);

            if (combo > 0) {
                paint.setTextSize(120); paint.setColor(isHyper ? Color.GREEN : Color.MAGENTA);
                canvas.drawText(getRank(), getWidth() - 250, 250, paint);
                paint.setTextSize(60);
                canvas.drawText(combo + " COMBO", getWidth() - 300, 320, paint);
            }

            if (flashAlpha > 0) {
                paint.setColor(Color.WHITE); paint.setAlpha(flashAlpha);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                flashAlpha *= 0.8f;
            }

            // СОХРАНЕНИЕ РЕКОРДА
            if (score > highScore) { 
                highScore = score; 
                prefs.edit().putInt("highScore", highScore).apply(); 
            }
            
            invalidate();
        }

        private void spawnExtraBalls(float x, float y) {
            if (balls.size() < 15) {
                balls.add(new Ball(x, y-10, -18, -14));
                balls.add(new Ball(x, y-10, 18, -14));
                flashAlpha = 60;
            }
        }

        private void resetGame() {
            balls.clear(); obstacles.clear(); combo = 0; score = 0;
            balls.add(new Ball(getWidth()/2f, 500, 15, 15));
        }

        private void explode(float x, float y, int c, int count) {
            for (int i=0; i<count; i++) particles.add(new Particle(x, y, c, 30, 25));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            paddleX = e.getX() - (paddleWidth/2);
            if (paddleX < 0) paddleX = 0; if (paddleX > getWidth()-paddleWidth) paddleX = getWidth()-paddleWidth;
            return true;
        }

        class FloatingText {
            float x, y, alpha = 255; String t; int c;
            FloatingText(float x, float y, String t, int c) { this.x = x; this.y = y; this.t = t; this.c = c; }
            boolean update() { y -= 5; alpha -= 7; return alpha > 0; }
            void draw(Canvas canvas, Paint p) {
                p.setColor(c); p.setAlpha((int)alpha); p.setTextSize(70);
                canvas.drawText(t, x, y, p);
            }
        }

        class Star {
            float x, y, size, speed; int alpha;
            Star() { x = random.nextInt(2000); y = random.nextInt(2500); size = 1+random.nextFloat()*5; speed = 4+random.nextFloat()*15; alpha = 100+random.nextInt(155); }
            void update(float f) { y += speed * f; if (y > 2500) { y = -50; x = random.nextInt(2000); } }
        }

        class Ball {
            float x, y, speedX, speedY;
            Ball(float x, float y, float sx, float sy) { this.x = x; this.y = y; this.speedX = sx; this.speedY = sy; }
            void update(float f, int sw) {
                x += speedX * f; y += speedY * f;
                if (x <= 30 || x >= sw - 30) { speedX = -speedX; x = x < 30 ? 31 : sw - 31; }
                if (y <= 30) { speedY = -speedY; y = 31; }
            }
            void draw(Canvas c, Paint p, boolean h) {
                p.setColor(h ? Color.CYAN : Color.WHITE);
                c.drawCircle(x, y, 25, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5);
                c.drawCircle(x, y, 35, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        class Obstacle {
            float x, y, vx, scale = 1.0f; int hp;
            boolean isSuper, isFast, isSplitter, isFragment, isBonus, isStone;
            float[] sR;
            Obstacle(float x, float y, boolean s, boolean f, boolean sp, boolean frag, boolean b, boolean st) {
                this.x = x; this.y = y; this.isSuper = s; this.isFast = f; this.isSplitter = sp; 
                this.isFragment = frag; this.isBonus = b; this.isStone = st; this.hp = s ? 2 : 1;
                if(st){ sR = new float[6]; for(int i=0; i<6; i++) sR[i] = 40+random.nextInt(40); }
            }
            int getColor() {
                if (isStone) return Color.GRAY; if (isBonus) return Color.GREEN;
                if (isSuper) return Color.YELLOW; if (isSplitter) return Color.MAGENTA;
                return isFast ? Color.CYAN : Color.RED;
            }
            void draw(Canvas c, Paint p) {
                p.setColor(getColor());
                if (isStone) {
                    Path path = new Path();
                    for(int i=0; i<6; i++) {
                        float a = (float)(i*Math.PI/3); float r = sR[i];
                        if(i==0) path.moveTo(x+(float)Math.cos(a)*r, y+(float)Math.sin(a)*r);
                        else path.lineTo(x+(float)Math.cos(a)*r, y+(float)Math.sin(a)*r);
                    }
                    path.close(); c.drawPath(path, p);
                } else if (isSuper) {
                    drawPoly(c, x, y, 55*scale, 10, true);
                } else if (isBonus) {
                    drawPoly(c, x, y, 50, 4, false);
                } else if (isSplitter) {
                    drawPoly(c, x, y, 50*scale, 3, false);
                } else {
                    c.drawRect(x-35, y-35, x+35, y+35, p);
                }
            }
            private void drawPoly(Canvas c, float cx, float cy, float r, int pts, boolean star) {
                Path path = new Path();
                for(int i=0; i<pts*2; i++) {
                    float radius = (star && i%2==0) ? r : r/2;
                    float a = (float)(i*Math.PI/pts);
                    if(i==0) path.moveTo(cx+(float)Math.cos(a)*radius, cy+(float)Math.sin(a)*radius);
                    else path.lineTo(cx+(float)Math.cos(a)*radius, cy+(float)Math.sin(a)*radius);
                }
                path.close(); c.drawPath(path, paint);
            }
        }

        class Particle {
            float x, y, vx, vy, life; int color;
            Particle(float x, float y, int c, float l, float v) {
                this.x = x; this.y = y; this.color = c; this.life = l == 0 ? 15 : l;
                this.vx = (random.nextFloat()-0.5f)*v; this.vy = (random.nextFloat()-0.5f)*v;
            }
            boolean update() { x += vx; y += vy; life--; return life > 0; }
            void draw(Canvas c, Paint p) {
                p.setColor(color); p.setAlpha((int)(life * 15));
                c.drawCircle(x, y, 8, p);
            }
        }
    }
}
