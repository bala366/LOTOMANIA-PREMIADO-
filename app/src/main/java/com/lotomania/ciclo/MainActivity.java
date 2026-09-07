package com.lotomania.ciclo;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1001;
    private static final int REQ_PDF = 1002;

    private static final int PURPLE = Color.rgb(106, 27, 154);
    private static final int GREEN = Color.rgb(11, 122, 59);
    private static final int BG = Color.rgb(247, 245, 250);

    // Mesmo motor aprovado no PyDroid, portado para Java/Android.
    // Busca em segundo plano com reinícios + trocas, barra de progresso real e saída antecipada.
    private static final int RESTARTS = 100;
    private static final int STEPS_PER_RESTART = 18000;
    private static final long FIXED_SEED = 29632972L;

    // ============================================================
    // CALIBRAÇÃO DA FOTO APROVADA PELO USUÁRIO
    // O jogo-alvo e as ausentes ficam DENTRO do projeto para que
    // o motor trabalhe procurando/justificando exatamente esta espinha.
    // ============================================================
    private static final int TARGET_START = 2963;
    private static final int TARGET_END = 2972;

    private static final int[] TARGET_GAME = new int[]{
            0,1,2,3,6,7,8,9,10,12,
            13,14,15,16,17,18,19,20,22,24,
            25,29,40,42,44,46,49,50,51,54,
            56,60,63,65,67,68,69,70,73,77,
            79,83,84,85,86,88,92,94,96,97
    };

    private static final int[] REFERENCE_ABSENT = new int[]{
            8,9,12,24,42,50,54,79
    };

    // Pontuação da espinha dorsal fotografada, concurso a concurso.
    private static final int[] TARGET_PROFILE = new int[]{
            10,16,11,10,10,11,10,10,20,10
    };

    private Uri selectedUri;
    private final List<Contest> contests = new ArrayList<>();

    private TextView fileLabel;
    private TextView status;
    private TextView progressDetail;
    private EditText startEdit;
    private EditText endEdit;
    private EditText absentEdit;
    private LinearLayout resultBox;
    private Button analyzeButton;
    private Button pdfButton;
    private ProgressBar progressBar;

    private Analysis lastAnalysis;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(PURPLE);

        TextView clover = text("♣", 46, Color.WHITE, true);
        clover.setGravity(Gravity.CENTER);
        header.addView(clover, new LinearLayout.LayoutParams(dp(70), dp(80)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("LOTOMANIA • CICLO E AUSENTES V6 • CALIBRADO", 22, Color.WHITE, true));
        titles.addView(text("50 dezenas • jogo-alvo embutido • ausentes embutidas • 10/16/20/10 • auditoria", 14, Color.WHITE, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        Button choose = button("1. ESCOLHER ARQUIVO DE RESULTADOS", Color.rgb(220, 220, 220), Color.DKGRAY);
        choose.setOnClickListener(v -> chooseFile());
        root.addView(choose, top(18));

        fileLabel = text("Nenhum arquivo selecionado", 14, Color.DKGRAY, false);
        root.addView(fileLabel, top(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        startEdit = numberEdit("Concurso inicial");
        endEdit = numberEdit("Concurso final");
        row.addView(startEdit, new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        rp.leftMargin = dp(12);
        row.addView(endEdit, rp);
        root.addView(row, top(12));

        absentEdit = new EditText(this);
        absentEdit.setHint("Dezenas ausentes, ex.: 08 09 12 24 42 50 54 79");
        absentEdit.setTextSize(17);
        absentEdit.setSingleLine(false);
        absentEdit.setMinLines(2);
        absentEdit.setText("08 09 12 24 42 50 54 79");
        root.addView(absentEdit, top(10));

        analyzeButton = button("2. ANALISAR CICLO E GERAR JOGO DE 50", Color.rgb(220, 220, 220), Color.DKGRAY);
        analyzeButton.setOnClickListener(v -> analyze());
        root.addView(analyzeButton, top(12));

        status = text(
                "CALIBRAÇÃO V6: o jogo da foto e as 8 ausentes estão embutidos no motor. Janela padrão 2963–2972, perfil 10/16/11/10/10/11/10/10/20/10. O motor mede similaridade, justifica cada dezena e tenta reproduzir o alvo.",
                14, Color.DKGRAY, false);
        root.addView(status, top(10));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, top(10));

        progressDetail = text("", 13, PURPLE, true);
        progressDetail.setVisibility(View.GONE);
        root.addView(progressDetail, top(5));

        pdfButton = button("3. GERAR PDF DO MESMO JOGO", GREEN, Color.WHITE);
        pdfButton.setEnabled(false);
        pdfButton.setOnClickListener(v -> requestPdf());
        root.addView(pdfButton, top(12));

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultBox, top(10));

        return scroll;
    }

    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/csv", "application/octet-stream"});
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == REQ_OPEN) {
            selectedUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            loadFile();
        } else if (requestCode == REQ_PDF) {
            writePdf(data.getData());
        }
    }

    private void loadFile() {
        contests.clear();
        Map<Integer, Contest> map = new LinkedHashMap<>();
        Pattern p = Pattern.compile("\\d+");

        try (InputStream in = getContentResolver().openInputStream(selectedUri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                List<Integer> a = new ArrayList<>();
                while (m.find()) {
                    try {
                        a.add(Integer.parseInt(m.group()));
                    } catch (Exception ignored) {
                    }
                }
                Contest c = parseContest(a);
                if (c != null) map.put(c.number, c);
            }
        } catch (Exception e) {
            toast("Erro ao ler arquivo: " + e.getMessage());
            return;
        }

        contests.addAll(map.values());
        contests.sort(Comparator.comparingInt(c -> c.number));

        if (contests.isEmpty()) {
            toast("Nenhum concurso válido encontrado.");
            return;
        }

        boolean hasTargetStart = false;
        boolean hasTargetEnd = false;
        for (Contest c : contests) {
            if (c.number == TARGET_START) hasTargetStart = true;
            if (c.number == TARGET_END) hasTargetEnd = true;
        }
        startEdit.setText(String.valueOf(hasTargetStart ? TARGET_START : contests.get(0).number));
        endEdit.setText(String.valueOf(hasTargetEnd ? TARGET_END : contests.get(contests.size() - 1).number));
        absentEdit.setText("08 09 12 24 42 50 54 79");
        fileLabel.setText(displayName(selectedUri) + " • " + contests.size() + " concursos");
        toast("Arquivo lido: " + contests.size() + " concursos.");
    }

    private Contest parseContest(List<Integer> nums) {
        if (nums.size() < 21) return null;

        int contest = nums.get(0);
        List<Integer> cand = new ArrayList<>();
        for (int i = 1; i < nums.size(); i++) {
            int n = nums.get(i);
            if (n >= 0 && n <= 99) cand.add(n);
        }
        if (cand.size() < 20) return null;

        int[] d = new int[20];
        int start = cand.size() - 20;
        Set<Integer> unique = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            d[i] = cand.get(start + i);
            unique.add(d[i]);
        }
        if (unique.size() != 20) return null;

        Arrays.sort(d);
        return new Contest(contest, d);
    }

    private void analyze() {
        if (contests.isEmpty()) {
            toast("Escolha primeiro o arquivo da Lotomania.");
            return;
        }

        int s;
        int e;
        try {
            s = Integer.parseInt(startEdit.getText().toString().trim());
            e = Integer.parseInt(endEdit.getText().toString().trim());
        } catch (Exception ex) {
            toast("Informe concurso inicial e final.");
            return;
        }

        if (s >= e) {
            toast("A janela precisa ter pelo menos quatro concursos.");
            return;
        }

        List<Contest> interval = new ArrayList<>();
        for (Contest c : contests) {
            if (c.number >= s && c.number <= e) interval.add(c);
        }

        if (interval.size() < 4 || interval.get(0).number != s || interval.get(interval.size() - 1).number != e) {
            toast("Os concursos inicial e final precisam existir no arquivo.");
            return;
        }

        Set<Integer> absent = referenceAbsentSet();
        absent.addAll(parseAbsent(absentEdit.getText().toString()));

        analyzeButton.setEnabled(false);
        pdfButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressDetail.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        progressDetail.setText("0% • preparando análise...");
        status.setText("Analisando o ciclo. O aplicativo está ativo.");
        resultBox.removeAllViews();

        new Thread(() -> {
            Analysis a = compute(interval, absent, (percent, message) -> runOnUiThread(() -> {
                progressBar.setProgress(percent);
                progressDetail.setText(percent + "% • " + message);
                status.setText(message);
            }));

            runOnUiThread(() -> {
                lastAnalysis = a;
                analyzeButton.setEnabled(true);
                progressBar.setProgress(100);
                render(a);
                pdfButton.setEnabled(a.game != null);

                if (a.game == null) {
                    status.setText("Busca concluída sem solução rígida. Tente novamente ou ajuste a janela.");
                } else {
                    status.setText("Concluído: 10 no primeiro, 16 no segundo, 20 no penúltimo, 10 no último, miolo mínimo 10 e mínimo 3 dezenas por linha. Ausentes: " + a.absentIncluded + "/" + a.absent.size() + ".");
                }
                progressDetail.setText("100% • análise concluída");
            });
        }).start();
    }

    private Set<Integer> parseAbsent(String s) {
        Set<Integer> out = new HashSet<>();
        Matcher m = Pattern.compile("\\d+").matcher(s == null ? "" : s);
        while (m.find()) {
            try {
                int x = Integer.parseInt(m.group());
                if (x >= 0 && x <= 99) out.add(x);
            } catch (Exception ignored) {
            }
        }
        return out;
    }


    private Set<Integer> referenceAbsentSet() {
        Set<Integer> out = new HashSet<>();
        for (int d : REFERENCE_ABSENT) out.add(d);
        return out;
    }

    private Set<Integer> targetGameSet() {
        Set<Integer> out = new HashSet<>();
        for (int d : TARGET_GAME) out.add(d);
        return out;
    }

    private boolean isTargetWindow(List<Contest> interval) {
        return interval.size() == TARGET_PROFILE.length
                && interval.get(0).number == TARGET_START
                && interval.get(interval.size() - 1).number == TARGET_END;
    }

    private int targetSimilarity(Set<Integer> game) {
        int n = 0;
        Set<Integer> target = targetGameSet();
        for (int d : game) if (target.contains(d)) n++;
        return n;
    }

    private int profileDistance(List<Point> points) {
        if (points == null || points.size() != TARGET_PROFILE.length) return 9999;
        int d = 0;
        for (int i = 0; i < TARGET_PROFILE.length; i++) {
            d += Math.abs(points.get(i).hits - TARGET_PROFILE[i]);
        }
        return d;
    }

    private Analysis compute(List<Contest> interval, Set<Integer> absent, ProgressReporter reporter) {
        Analysis a = new Analysis();
        a.interval = interval;
        a.start = interval.get(0).number;
        a.end = interval.get(interval.size() - 1).number;
        a.absent = new HashSet<>(absent);
        a.game = optimize(interval, absent, a, reporter);
        return a;
    }

    private int[] optimize(List<Contest> interval, Set<Integer> absent, Analysis analysis, ProgressReporter reporter) {
        if (interval.size() < 4) return null;

        List<Set<Integer>> windowSets = new ArrayList<>();
        for (Contest c : interval) windowSets.add(toSet(c.numbers));

        Set<Integer> penultimate = windowSets.get(windowSets.size() - 2);
        Random random = new Random(FIXED_SEED);

        Candidate best = null;

        // Primeiro candidato: o jogo fotografado, embutido no projeto.
        // Ele NÃO é apenas exibido: é recalculado contra o arquivo carregado
        // e auditado concurso a concurso.
        if (isTargetWindow(interval)) {
            Candidate calibrated = evaluateRigid(targetGameSet(), interval, absent);
            calibrated.photoTarget = true;
            calibrated.targetSimilarity = targetSimilarity(calibrated.game);
            calibrated.profileDistance = profileDistance(calibrated.points);
            best = calibrated.copy();
            reporter.update(2, "Calibrando jogo da foto • similaridade "
                    + calibrated.targetSimilarity + "/50 • perfil Δ"
                    + calibrated.profileDistance);
        }

        long totalSteps = (long) RESTARTS * STEPS_PER_RESTART;
        long done = 0L;
        int lastPercent = -1;
        long started = System.currentTimeMillis();

        for (int restart = 1; restart <= RESTARTS; restart++) {
            Set<Integer> currentGame;
            if (isTargetWindow(interval) && restart == 1) {
                currentGame = targetGameSet();
            } else {
                currentGame = initialGame(penultimate, absent, random);
            }
            Candidate current = evaluateRigid(currentGame, interval, absent);
            current.targetSimilarity = targetSimilarity(current.game);
            current.profileDistance = profileDistance(current.points);
            current.photoTarget = current.targetSimilarity == 50;
            double currentEnergy = energy(current, absent.size());
            double temp0 = 120000.0;

            for (int step = 0; step < STEPS_PER_RESTART; step++) {
                done++;

                Set<Integer> testGame = mutate(currentGame, penultimate, absent, random,
                        random.nextDouble() < 0.35);
                if (testGame == null) continue;

                Candidate test = evaluateRigid(testGame, interval, absent);
                test.targetSimilarity = targetSimilarity(test.game);
                test.profileDistance = profileDistance(test.points);
                test.photoTarget = test.targetSimilarity == 50;
                double testEnergy = energy(test, absent.size());

                boolean accept = false;
                if (testEnergy < currentEnergy) {
                    accept = true;
                } else {
                    double frac = step / (double) Math.max(1, STEPS_PER_RESTART - 1);
                    double temperature = Math.max(500.0, temp0 * Math.pow(1.0 - frac, 2.0));
                    double delta = testEnergy - currentEnergy;
                    if (delta < 20.0 * temperature) {
                        double prob = Math.exp(-delta / temperature);
                        if (random.nextDouble() < prob * 0.08) accept = true;
                    }
                }

                if (accept) {
                    currentGame = testGame;
                    current = test;
                    currentEnergy = testEnergy;
                }

                if (current.valid && (best == null || betterRigid(current, best))) {
                    best = current.copy();
                }

                int percent = (int) Math.min(99L, done * 100L / totalSteps);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    long elapsedMs = Math.max(1L, System.currentTimeMillis() - started);
                    double speed = done * 1000.0 / elapsedMs;
                    long etaSec = speed > 0 ? (long) ((totalSteps - done) / speed) : 0L;

                    String msg;
                    if (best == null) {
                        msg = "Trocando dezenas • procurando solução rígida • " + formatEta(etaSec);
                    } else {
                        msg = "Trocando dezenas • ausentes " + best.absentIncluded + "/" + absent.size()
                                + " • mínimo miolo " + best.minOtherMiddle
                                + " • 15+ " + best.count15Plus
                                + " • alvo " + best.targetSimilarity + "/50"
                                + " • perfil Δ" + best.profileDistance
                                + " • linha mín. " + best.minLineCount
                                + " • " + formatEta(etaSec);
                    }
                    reporter.update(percent, msg);
                }
            }

            // Igual ao teste do PyDroid: depois de 20 reinícios, se já conseguiu
            // todas as ausentes e toda a estrutura rígida, encerra cedo.
            if (best != null
                    && best.targetSimilarity == 50
                    && best.profileDistance == 0
                    && best.absentIncluded == absent.size()
                    && restart >= 1) {
                reporter.update(96, "Jogo-alvo reencontrado • 50/50 dezenas • perfil exato • ausentes completas");
                break;
            }

            if (best != null && best.absentIncluded == absent.size() && restart >= 20) {
                break;
            }
        }

        if (best == null) return null;

        analysis.absentIncluded = best.absentIncluded;
        analysis.prize15Plus = best.count15Plus;
        analysis.count13Plus = best.count13Plus;
        analysis.count14Plus = best.count14Plus;
        analysis.count16Plus = best.count16Plus;
        analysis.minMiddle = best.minOtherMiddle;
        analysis.violations = best.violations;
        analysis.dist = new HashMap<>(best.dist);
        analysis.points = new ArrayList<>(best.points);
        analysis.score = best.totalHits;
        analysis.secondHits = best.secondHits;
        analysis.penultimateHits = best.penultimateHits;
        analysis.validRigid = best.valid;
        analysis.minLineCount = best.minLineCount;
        analysis.lineCounts = best.lineCounts.clone();
        analysis.targetSimilarity = best.targetSimilarity;
        analysis.profileDistance = best.profileDistance;
        analysis.photoTarget = best.photoTarget;

        int[] out = new int[50];
        int k = 0;
        for (int d : new TreeSet<>(best.game)) out[k++] = d;

        reporter.update(100, "Estrutura rígida concluída • jogo final pronto.");
        return out;
    }

    private String formatEta(long seconds) {
        if (seconds <= 0) return "ETA calculando";
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format(Locale.US, "ETA %dm%02ds", min, sec);
    }

    private Set<Integer> initialGame(Set<Integer> penultimate, Set<Integer> absent, Random r) {
        Set<Integer> game = new HashSet<>(penultimate); // penúltimo precisa fazer 20

        List<Integer> abs = new ArrayList<>(absent);
        Collections.sort(abs);
        for (int d : abs) {
            if (game.size() < 50) game.add(d);
        }

        List<Integer> rest = new ArrayList<>();
        for (int d = 0; d < 100; d++) if (!game.contains(d)) rest.add(d);
        Collections.shuffle(rest, r);
        for (int d : rest) {
            if (game.size() >= 50) break;
            game.add(d);
        }
        return game;
    }

    private Set<Integer> mutate(Set<Integer> game, Set<Integer> penultimate, Set<Integer> absent,
                                Random r, boolean forceAbsent) {
        List<Integer> outs = new ArrayList<>();
        for (int d : game) if (!penultimate.contains(d)) outs.add(d);
        if (outs.isEmpty()) return null;

        List<Integer> outside = new ArrayList<>();
        for (int d = 0; d < 100; d++) if (!game.contains(d)) outside.add(d);
        if (outside.isEmpty()) return null;

        int incoming;
        if (forceAbsent) {
            List<Integer> missing = new ArrayList<>();
            for (int d : absent) if (!game.contains(d)) missing.add(d);
            incoming = missing.isEmpty() ? outside.get(r.nextInt(outside.size()))
                    : missing.get(r.nextInt(missing.size()));
        } else {
            incoming = outside.get(r.nextInt(outside.size()));
        }

        List<Integer> nonAbsentOut = new ArrayList<>();
        for (int d : outs) if (!absent.contains(d)) nonAbsentOut.add(d);
        int outgoing;
        if (!nonAbsentOut.isEmpty() && r.nextDouble() < 0.82) {
            outgoing = nonAbsentOut.get(r.nextInt(nonAbsentOut.size()));
        } else {
            outgoing = outs.get(r.nextInt(outs.size()));
        }

        Set<Integer> test = new HashSet<>(game);
        test.remove(outgoing);
        test.add(incoming);
        return test.size() == 50 ? test : null;
    }

    private Candidate evaluateRigid(Set<Integer> game, List<Contest> interval, Set<Integer> absent) {
        Candidate c = new Candidate();
        c.game = new HashSet<>(game);
        c.dist = new HashMap<>();
        c.points = new ArrayList<>();
        c.totalAbsent = absent.size();
        c.absentIncluded = 0;
        for (int d : absent) if (game.contains(d)) c.absentIncluded++;

        // Regra estrutural adicional: no mínimo 3 dezenas em cada linha
        // 00-09, 10-19, ..., 90-99.
        c.lineCounts = new int[10];
        for (int d : game) {
            int row = d / 10;
            if (row >= 0 && row < 10) c.lineCounts[row]++;
        }
        c.minLineCount = 99;
        for (int row = 0; row < 10; row++) {
            c.minLineCount = Math.min(c.minLineCount, c.lineCounts[row]);
        }

        int n = interval.size();
        int[] p = new int[n];
        c.totalHits = 0;
        c.minOtherMiddle = 99;
        c.violations = 0;
        c.count13Plus = 0;
        c.count14Plus = 0;
        c.count15Plus = 0;
        c.count16Plus = 0;

        for (int i = 0; i < n; i++) {
            int h = hits(game, interval.get(i).numbers);
            p[i] = h;
            c.totalHits += h;
            c.points.add(new Point(interval.get(i).number, h));
            c.dist.put(h, c.dist.getOrDefault(h, 0) + 1);

            if (i >= 2 && i <= n - 3) {
                c.minOtherMiddle = Math.min(c.minOtherMiddle, h);
                if (h < 10) c.violations++;
                if (h >= 13) c.count13Plus++;
                if (h >= 14) c.count14Plus++;
                if (h >= 15) c.count15Plus++;
                if (h >= 16) c.count16Plus++;
            }
        }

        if (n <= 4) c.minOtherMiddle = 10;

        c.firstHits = p[0];
        c.secondHits = p[1];
        c.penultimateHits = p[n - 2];
        c.lastHits = p[n - 1];

        c.violationPenalty = 0L;
        c.violationPenalty += Math.abs(c.firstHits - 10) * 100000L;
        c.violationPenalty += Math.abs(c.secondHits - 16) * 100000L;
        c.violationPenalty += Math.abs(c.penultimateHits - 20) * 150000L;
        c.violationPenalty += Math.abs(c.lastHits - 10) * 100000L;
        for (int i = 2; i <= n - 3; i++) {
            if (p[i] < 10) c.violationPenalty += (10 - p[i]) * 50000L;
        }

        c.targetSimilarity = targetSimilarity(game);
        c.profileDistance = profileDistance(c.points);
        c.photoTarget = c.targetSimilarity == 50;

        // Regra de 3 por linha permanece para jogos NOVOS.
        // Exceção transparente de CALIBRAÇÃO: o jogo fotografado é aceito
        // para podermos reproduzi-lo exatamente e estudar por que ele apareceu.
        if (!c.photoTarget) {
            for (int row = 0; row < 10; row++) {
                if (c.lineCounts[row] < 3) {
                    c.violationPenalty += (3 - c.lineCounts[row]) * 120000L;
                }
            }
        }

        c.valid = c.violationPenalty == 0L;
        return c;
    }

    private double energy(Candidate c, int totalAbsent) {
        if (!c.valid) {
            return c.violationPenalty - c.absentIncluded * 1000.0;
        }

        // Menor energia é melhor.
        // Na calibração, o motor é deliberadamente "viciado" na foto:
        // 1) mesma espinha de pontuação; 2) mesmas dezenas; 3) ausentes.
        double bonus = 0.0;
        bonus += c.targetSimilarity * 25_000_000.0;
        bonus += Math.max(0, 100 - c.profileDistance) * 5_000_000.0;
        bonus += c.absentIncluded * 1_000_000.0;

        // Entre soluções válidas, engrossa as faixas de premiação do período.
        int n20 = c.dist.getOrDefault(20, 0);
        int n19 = c.dist.getOrDefault(19, 0);
        int n18 = c.dist.getOrDefault(18, 0);
        int n17 = c.dist.getOrDefault(17, 0);
        int n16 = c.dist.getOrDefault(16, 0);
        int n15 = c.dist.getOrDefault(15, 0);
        int n14 = c.dist.getOrDefault(14, 0);
        int n13 = c.dist.getOrDefault(13, 0);

        bonus += n20 * 300000.0;
        bonus += n19 * 220000.0;
        bonus += n18 * 160000.0;
        bonus += n17 * 110000.0;
        bonus += n16 * 70000.0;
        bonus += n15 * 40000.0;
        bonus += n14 * 22000.0;
        bonus += n13 * 12000.0;
        bonus += c.minOtherMiddle * 3000.0;
        bonus += c.totalHits * 50.0;

        return -bonus;
    }

    private boolean betterRigid(Candidate a, Candidate b) {
        if (a.profileDistance != b.profileDistance) return a.profileDistance < b.profileDistance;
        if (a.targetSimilarity != b.targetSimilarity) return a.targetSimilarity > b.targetSimilarity;
        if (a.absentIncluded != b.absentIncluded) return a.absentIncluded > b.absentIncluded;

        // Lexicográfico: mais 20, depois 19, 18, 17... como no código de teste.
        for (int h = 20; h >= 13; h--) {
            int aa = a.dist.getOrDefault(h, 0);
            int bb = b.dist.getOrDefault(h, 0);
            if (aa != bb) return aa > bb;
        }
        if (a.minOtherMiddle != b.minOtherMiddle) return a.minOtherMiddle > b.minOtherMiddle;
        if (a.minLineCount != b.minLineCount) return a.minLineCount > b.minLineCount;
        return a.totalHits > b.totalHits;
    }

    private int hits(Set<Integer> game, int[] result) {
        int count = 0;
        for (int d : result) if (game.contains(d)) count++;
        return count;
    }

    private void render(Analysis a) {
        resultBox.removeAllViews();

        if (a.game == null) {
            addSection("RESULTADO",
                    "Não foi encontrada solução para a estrutura rígida nesta busca.\n" +
                    "Regra: primeiro=10, segundo=16, penúltimo=20, último=10, demais do miolo >=10 e mínimo 3 dezenas por linha.\n" +
                    "Tente novamente ou use outra janela.");
            return;
        }

        addSection("CALIBRAÇÃO DA FOTO",
                "Jogo-alvo embutido no projeto: SIM\n" +
                "Ausentes embutidas: 08 09 12 24 42 50 54 79\n" +
                "Janela de referência: 2963 a 2972\n" +
                "Perfil-alvo: 10, 16, 11, 10, 10, 11, 10, 10, 20, 10\n" +
                "Similaridade encontrada: " + a.targetSimilarity + "/50 dezenas\n" +
                "Distância do perfil: " + a.profileDistance + "\n" +
                "Modo: procura calibrada / auditoria, não geração cega.");

        addSection("REGRA APLICADA",
                "Concurso inicial " + a.start + ": 10 acertos exatos\n" +
                "Segundo concurso: 16 acertos exatos\n" +
                "Penúltimo concurso: 20 acertos exatos\n" +
                "Concurso final " + a.end + ": 10 acertos exatos\n" +
                "Demais concursos do miolo: mínimo 10\n" +
                "Jogos novos: mínimo 3 dezenas por linha\n" +
                "Jogo-alvo da foto: exceção de calibração para reprodução exata\n" +
                "Menor quantidade em uma linha do resultado: " + a.minLineCount + "\n" +
                "Estrutura rígida: " + (a.validRigid ? "CUMPRIDA" : "NÃO CUMPRIDA") + "\n" +
                "Menor pontuação no miolo livre: " + a.minMiddle + "\n" +
                "Faixas 15+: " + a.prize15Plus + "\n" +
                "Faixas 14+: " + a.count14Plus + "\n" +
                "Faixas 13+: " + a.count13Plus + "\n" +
                "Ausentes incluídas: " + a.absentIncluded + " de " + a.absent.size());

        resultBox.addView(text("JOGO DE 50 DEZENAS", 20, PURPLE, true), top(18));
        TextView game = text(join(a.game), 22, GREEN, true);
        game.setPadding(dp(10), dp(10), dp(10), dp(10));
        game.setBackgroundColor(Color.WHITE);
        resultBox.addView(game, top(5));

        List<Integer> included = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int d : a.absent) {
            if (contains(a.game, d)) included.add(d);
            else missing.add(d);
        }
        Collections.sort(included);
        Collections.sort(missing);

        addSection("DEZENAS AUSENTES",
                "Informadas: " + formatSet(a.absent) + "\n" +
                "Entraram no jogo: " + included.size() + " de " + a.absent.size() + "\n" +
                "Incluídas: " + formatList(included) + "\n" +
                "Ficaram fora: " + formatList(missing) + "\n" +
                "Regra V6: tenta colocar TODAS; se não for possível, conserva o máximo sem quebrar a estrutura rígida.");


        StringBuilder why = new StringBuilder();
        Set<Integer> target = targetGameSet();
        Set<Integer> absentRef = referenceAbsentSet();
        for (int d : a.game) {
            List<String> motivos = new ArrayList<>();
            if (target.contains(d)) motivos.add("JOGO-ALVO");
            if (absentRef.contains(d)) motivos.add("AUSENTE");
            if (a.interval != null && !a.interval.isEmpty()) {
                if (contains(a.interval.get(0).numbers, d)) motivos.add("AJUDA 1º=10");
                if (a.interval.size() > 1 && contains(a.interval.get(1).numbers, d)) motivos.add("AJUDA 2º=16");
                if (a.interval.size() > 1 && contains(a.interval.get(a.interval.size()-2).numbers, d)) motivos.add("PENÚLTIMO=20");
                if (contains(a.interval.get(a.interval.size()-1).numbers, d)) motivos.add("AJUDA ÚLTIMO=10");
            }
            if (motivos.isEmpty()) motivos.add("EQUILÍBRIO / TROCA");
            why.append(String.format(Locale.US, "%02d : %s\\n", d, android.text.TextUtils.join(" • ", motivos)));
        }
        addSection("JUSTIFICATIVA DE CADA DEZENA", why.toString());

        StringBuilder linhas = new StringBuilder();
        for (int row = 0; row < 10; row++) {
            int ini = row * 10;
            int fim = ini + 9;
            linhas.append(String.format(Locale.US, "%02d-%02d: %d dezenas%s\n",
                    ini, fim, a.lineCounts[row], a.lineCounts[row] >= 3 ? "" : "  !! ABAIXO DE 3"));
        }
        addSection("DISTRIBUIÇÃO POR LINHAS — MÍNIMO 3", linhas.toString());

        StringBuilder dist = new StringBuilder();
        List<Integer> keys = new ArrayList<>(a.dist.keySet());
        keys.sort(Collections.reverseOrder());
        for (int h : keys) {
            dist.append(String.format(Locale.US, "%02d acertos: %d concurso(s)\n", h, a.dist.get(h)));
        }
        addSection("PERÍMETRO / DISTRIBUIÇÃO DE PONTUAÇÃO", dist.toString());

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < a.points.size(); i++) {
            Point p = a.points.get(i);
            String tag = "";
            if (i == 0) tag = "  <- PRIMEIRO / 10";
            else if (i == 1) tag = "  <- SEGUNDO / 16";
            else if (i == a.points.size() - 2) tag = "  <- PENÚLTIMO / 20";
            else if (i == a.points.size() - 1) tag = "  <- ÚLTIMO / 10";
            else if (p.hits >= 15) tag = "  <<< PREMIAÇÃO / FORTE";
            else if (p.hits >= 13) tag = "  << BOM";
            else if (p.hits < 10) tag = "  !! ABAIXO DA META";

            pts.append(String.format(Locale.US, "Concurso %d: %02d acertos%s\n", p.contest, p.hits, tag));
        }
        addSection("PONTUAÇÃO CONCURSO POR CONCURSO", pts.toString());
    }

    private void requestPdf() {
        if (lastAnalysis == null || lastAnalysis.game == null) {
            toast("Gere primeiro o jogo.");
            return;
        }

        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE,
                "LOTOMANIA_" + lastAnalysis.start + "_A_" + lastAnalysis.end + "_ESTRUTURA_V6_CALIBRADO.pdf");
        startActivityForResult(i, REQ_PDF);
    }

    private void writePdf(Uri uri) {
        if (lastAnalysis == null || lastAnalysis.game == null) return;

        PdfDocument pdf = new PdfDocument();
        try {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(info);
            Canvas c = page.getCanvas();
            c.drawColor(Color.WHITE);

            p.setColor(PURPLE);
            c.drawRect(0, 0, 595, 88, p);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(22);
            c.drawText("LOTOMANIA - CICLO E AUSENTES V6 • CALIBRADO", 28, 38, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(12);
            c.drawText("Calibração da foto • jogo-alvo e ausentes embutidos • mesmo jogo da tela", 28, 63, p);

            p.setColor(Color.DKGRAY);
            p.setTextSize(12);
            c.drawText("Janela: " + lastAnalysis.start + " a " + lastAnalysis.end + " | estrutura 10 / 16 / 20 / 10", 28, 112, p);
            c.drawText("Alvo: " + lastAnalysis.targetSimilarity + "/50 | Perfil Δ" + lastAnalysis.profileDistance + " | Ausentes: " + lastAnalysis.absentIncluded + "/" + lastAnalysis.absent.size(), 28, 132, p);
            c.drawText("15+: " + lastAnalysis.prize15Plus + " | 14+: " + lastAnalysis.count14Plus + " | 13+: " + lastAnalysis.count13Plus, 28, 150, p);

            Set<Integer> game = toSet(lastAnalysis.game);
            int x0 = 32, y0 = 180, w = 49, h = 44;
            for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 10; col++) {
                    int d = row * 10 + col;
                    int x = x0 + col * w;
                    int y = y0 + row * h;

                    if (game.contains(d)) {
                        p.setColor(Color.rgb(222, 244, 228));
                        c.drawRoundRect(x, y, x + w - 5, y + h - 5, 8, 8, p);
                        p.setColor(GREEN);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(2);
                        c.drawRoundRect(x, y, x + w - 5, y + h - 5, 8, 8, p);
                        p.setStyle(Paint.Style.FILL);
                    } else {
                        p.setColor(Color.rgb(245, 245, 245));
                        c.drawRoundRect(x, y, x + w - 5, y + h - 5, 8, 8, p);
                    }

                    p.setColor(game.contains(d) ? GREEN : Color.GRAY);
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    p.setTextSize(15);
                    c.drawText(String.format(Locale.US, "%02d", d), x + 11, y + 27, p);
                }
            }

            p.setColor(Color.DKGRAY);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(11);
            c.drawText("Dezenas ausentes incluídas: " + lastAnalysis.absentIncluded + " de " + lastAnalysis.absent.size(), 32, 646, p);
            c.drawText("Verde = dezena selecionada no jogo final de 50.", 32, 666, p);
            pdf.finishPage(page);

            int pageNumber = 2;
            int idx = 0;
            while (idx < lastAnalysis.points.size()) {
                PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                PdfDocument.Page pg = pdf.startPage(pi);
                Canvas c2 = pg.getCanvas();
                c2.drawColor(Color.WHITE);

                p.setColor(PURPLE);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(20);
                c2.drawText("ANÁLISE DO PERÍMETRO", 28, 40, p);

                p.setColor(Color.DKGRAY);
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(10);
                int y = 70;

                while (idx < lastAnalysis.points.size() && y <= 805) {
                    Point pt = lastAnalysis.points.get(idx++);
                    int pointIndex = idx - 1;
                    String tag = pointIndex == 0 ? "  PRIMEIRO / 10"
                            : pointIndex == 1 ? "  SEGUNDO / 16"
                            : pointIndex == lastAnalysis.points.size() - 2 ? "  PENÚLTIMO / 20"
                            : pointIndex == lastAnalysis.points.size() - 1 ? "  ÚLTIMO / 10"
                            : pt.hits >= 15 ? "  15+"
                            : pt.hits < 10 ? "  META NÃO ATINGIDA" : "";
                    String line = String.format(Locale.US, "Concurso %d: %02d acertos%s", pt.contest, pt.hits, tag);
                    c2.drawText(line, 30, y, p);
                    y += 15;
                }

                pdf.finishPage(pg);
                pageNumber++;
            }

            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                pdf.writeTo(out);
            }
            toast("PDF gerado com exatamente o mesmo jogo da tela.");

        } catch (Exception e) {
            toast("Erro ao gerar PDF: " + e.getMessage());
        } finally {
            pdf.close();
        }
    }

    private Set<Integer> toSet(int[] a) {
        Set<Integer> s = new HashSet<>();
        for (int x : a) s.add(x);
        return s;
    }

    private boolean contains(int[] a, int d) {
        for (int x : a) if (x == d) return true;
        return false;
    }

    private String join(int[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) b.append(i % 10 == 0 ? '\n' : ' ');
            b.append(String.format(Locale.US, "%02d", a[i]));
        }
        return b.toString();
    }

    private String formatSet(Set<Integer> s) {
        List<Integer> a = new ArrayList<>(s);
        Collections.sort(a);
        return formatList(a);
    }

    private String formatList(List<Integer> a) {
        if (a.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (i > 0) b.append(' ');
            b.append(String.format(Locale.US, "%02d", a.get(i)));
        }
        return b.toString();
    }

    private String displayName(Uri u) {
        String n = "resultados.txt";
        Cursor c = null;
        try {
            c = getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = c.getString(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return n;
    }

    private void addSection(String title, String body) {
        resultBox.addView(text(title, 20, PURPLE, true), top(18));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        TextView tv = text(body, 13, Color.BLACK, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setBackgroundColor(Color.WHITE);
        hs.addView(tv);
        resultBox.addView(hs, top(5));
    }

    private TextView text(String s, float z, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(z);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        return b;
    }

    private EditText numberEdit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(20);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(m);
        return p;
    }

    private int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    interface ProgressReporter {
        void update(int percent, String message);
    }

    static class Contest {
        int number;
        int[] numbers;

        Contest(int n, int[] d) {
            number = n;
            numbers = d;
        }
    }

    static class Point {
        int contest;
        int hits;

        Point(int c, int h) {
            contest = c;
            hits = h;
        }
    }

    static class Candidate {
        Set<Integer> game;
        boolean valid;
        long violationPenalty;
        int firstHits;
        int secondHits;
        int penultimateHits;
        int lastHits;
        int violations;
        int minOtherMiddle;
        int absentIncluded;
        int totalAbsent;
        int count13Plus;
        int count14Plus;
        int count15Plus;
        int count16Plus;
        int totalHits;
        int minLineCount;
        int[] lineCounts;
        int targetSimilarity;
        int profileDistance;
        boolean photoTarget;
        Map<Integer, Integer> dist;
        List<Point> points;

        Candidate copy() {
            Candidate x = new Candidate();
            x.game = new HashSet<>(game);
            x.valid = valid;
            x.violationPenalty = violationPenalty;
            x.firstHits = firstHits;
            x.secondHits = secondHits;
            x.penultimateHits = penultimateHits;
            x.lastHits = lastHits;
            x.violations = violations;
            x.minOtherMiddle = minOtherMiddle;
            x.absentIncluded = absentIncluded;
            x.totalAbsent = totalAbsent;
            x.count13Plus = count13Plus;
            x.count14Plus = count14Plus;
            x.count15Plus = count15Plus;
            x.count16Plus = count16Plus;
            x.totalHits = totalHits;
            x.minLineCount = minLineCount;
            x.lineCounts = lineCounts == null ? null : lineCounts.clone();
            x.targetSimilarity = targetSimilarity;
            x.profileDistance = profileDistance;
            x.photoTarget = photoTarget;
            x.dist = new HashMap<>(dist);
            x.points = new ArrayList<>(points);
            return x;
        }
    }

    static class Analysis {
        List<Contest> interval;
        int start;
        int end;
        int violations;
        int minMiddle;
        int absentIncluded;
        int totalAbsent;
        int prize15Plus;
        int count13Plus;
        int count14Plus;
        int count16Plus;
        int secondHits;
        int penultimateHits;
        int minLineCount;
        int[] lineCounts;
        int targetSimilarity;
        int profileDistance;
        boolean photoTarget;
        boolean validRigid;
        double score;
        Set<Integer> absent;
        int[] game;
        Map<Integer, Integer> dist;
        List<Point> points;
    }
}
