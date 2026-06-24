package com.photomanager.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 标记完成时的绿色星光粒子特效。
 *
 * 用法：
 *   DoneEffectDialog.showAt(component, x, y);
 *
 * 在 component 坐标系的 (x,y) 处弹出透明小窗口，
 * 绿色星光粒子从该点爆开并快速下落消散。
 */
public class DoneEffectDialog {

    private static final int PARTICLE_COUNT  = 22;
    private static final int DURATION_MS    = 820;   // 总时长 ms
    private static final int TIMER_INTERVAL = 14;    // 帧间隔 ms
    private static final float GRAVITY     = 0.16f;

    /* ========== 静态入口 ========== */

    public static void showAt(Component parent, int x, int y) {
        // 将 parent 坐标系的 (x,y) 转为屏幕坐标
        Point screenPt = SwingUtilities.convertPoint(parent, x, y, null);

        // 特效窗口尺寸
        int w = 300, h = 400;
        JWindow win = new JWindow();
        win.setAlwaysOnTop(true);
        win.setBounds(screenPt.x - w / 2, screenPt.y - h / 3, w, h);
        win.setBackground(new Color(0, 0, 0, 0));

        // 粒子爆开中心（窗口坐标）
        int cx = w / 2;
        int cy = h / 3;

        // 创建粒子
        Random rnd = new Random();
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(cx, cy, rnd));
        }

        // 绘制面板
        ParticlePanel panel = new ParticlePanel(particles);
        panel.setOpaque(false);
        win.setContentPane(panel);
        win.setVisible(true);

        long[] start = {System.currentTimeMillis()};
        Timer timer = new Timer(TIMER_INTERVAL, null);
        timer.addActionListener(e -> {
            long elapsed = System.currentTimeMillis() - start[0];
            float progress = Math.min(1.0f, (float) elapsed / DURATION_MS);

            for (Particle p : particles) {
                p.update(progress);
            }
            panel.repaint();

            if (progress >= 1.0f) {
                timer.stop();
                win.dispose();
            }
        });
        timer.start();
    }

    /* ========== 粒子 ========== */

    private static class Particle {
        float x, y;
        float vx, vy;
        float size, maxSize;
        float alpha;
        int red, green, blue;

        Particle(int cx, int cy, Random rnd) {
            float spread = 22f;
            this.x = cx + (rnd.nextFloat() - 0.5f) * spread;
            this.y = cy + (rnd.nextFloat() - 0.5f) * spread * 0.3f;
            this.vx = (rnd.nextFloat() - 0.5f) * 2.6f;
            this.vy = 1.6f + rnd.nextFloat() * 3.2f;
            this.maxSize = 2.5f + rnd.nextFloat() * 5.5f;
            this.size = 0f;
            // 绿色系，带暖调
            this.red   = 50 + rnd.nextInt(120);   // 50~170
            this.green = 140 + rnd.nextInt(110);  // 140~250
            this.blue  = 30 + rnd.nextInt(80);    // 30~110
            this.alpha = 0f;
        }

        void update(float progress) {
            vy += GRAVITY;
            x  += vx;
            y  += vy;

            // 大小：先放大（0~0.18），再缩小
            if (progress < 0.18f) {
                size = maxSize * (progress / 0.18f);
            } else {
                float t = (progress - 0.18f) / 0.82f;
                size = maxSize * (1.0f - t * 0.88f);
                if (size < 0) size = 0;
            }

            // 透明度：先出现（0~0.10），再消失
            if (progress < 0.10f) {
                alpha = progress / 0.10f;
            } else {
                alpha = 1.0f - (progress - 0.10f) / 0.90f;
            }
            if (alpha < 0) alpha = 0;
            if (alpha > 1) alpha = 1;
        }
    }

    /* ========== 绘制面板 ========== */

    private static class ParticlePanel extends JPanel {
        private final List<Particle> particles;

        ParticlePanel(List<Particle> particles) {
            this.particles = particles;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            for (Particle p : particles) {
                if (p.alpha <= 0.01f || p.size < 0.3f) continue;

                int d = (int) (p.size * 2);
                if (d < 1) d = 1;
                int px = (int) (p.x - d / 2);
                int py = (int) (p.y - d / 2);

                // 光晕层（大圆，低透明度）
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, p.alpha * 0.18f));
                g2.setColor(new Color(p.red, p.green, p.blue));
                int halo = d * 4;
                g2.fillOval(px - halo / 2 + d / 2,
                             py - halo / 2 + d / 2,
                             halo, halo);

                // 核心亮点
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, p.alpha * 0.9f));
                g2.setColor(new Color(p.red, p.green, p.blue));
                g2.fillOval(px, py, d, d);

                // 十字星光射线
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, p.alpha * 0.45f));
                g2.setStroke(new BasicStroke(1.0f));
                int ray = (int) (p.size * 3.5f);
                int cx = px + d / 2;
                int cy = py + d / 2;
                g2.drawLine(cx - ray, cy, cx + ray, cy);
                g2.drawLine(cx, cy - ray, cx, cy + ray);
                int dr = (int) (ray * 0.6f);
                g2.drawLine(cx - dr, cy - dr, cx + dr, cy + dr);
                g2.drawLine(cx - dr, cy + dr, cx + dr, cy - dr);
            }

            // 恢复 composite
            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 1.0f));
        }
    }
}
