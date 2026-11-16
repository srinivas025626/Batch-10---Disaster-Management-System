import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/* ============================================================
 *  DISASTER MANAGEMENT SYSTEM  (CGB1201 - Java Programming)
 *  Modules: 1 Detection | 2 Severity | 3 Alerts | 4 Resources | 5 Response
 * ============================================================ */

/** Custom exception raised when stock is not enough (Module 4). */
class ShortageException extends Exception {
    public ShortageException(String msg) { super(msg); }
}

enum Level { LOW, MODERATE, HIGH, CRITICAL }
enum Status { REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED }

/** Disaster object: stores casualties, area, intensity (Module 1) and the score (Module 2). */
class Disaster implements Comparable<Disaster> {
    private static int counter = 0;
    final int id;
    final String type, location;
    final int intensity, casualties;   // intensity on 1-10 scale
    final double area;                 // sq km
    double intensityPts, casualtyPts, areaPts, score;
    Level level;
    Status status = Status.REPORTED;
    boolean assessed = false;

    Disaster(String type, String location, int intensity, int casualties, double area) {
        this.id = ++counter;
        this.type = type; this.location = location;
        this.intensity = intensity; this.casualties = casualties; this.area = area;
    }
    static void resetCounter() { counter = 0; }
    String label() { return type + " at " + location; }

    /** Severity score out of 100: intensity (40) + casualties (30) + area (30). */
    void assess() {
        intensityPts = intensity * 40.0 / 10.0;              // 10 -> 40
        casualtyPts  = Math.min(30.0, casualties * 30.0 / 100.0); // 100+ -> 30
        areaPts      = Math.min(30.0, area * 30.0 / 500.0);       // 500+ sq km -> 30
        score = Math.round((intensityPts + casualtyPts + areaPts) * 10) / 10.0;
        level = score >= 75 ? Level.CRITICAL : score >= 50 ? Level.HIGH
              : score >= 25 ? Level.MODERATE : Level.LOW;
        assessed = true;
    }
    String breakdown() {
        return String.format("Score = intensity %.1f + casualties %.1f + area %.1f = %.1f -> %s",
                intensityPts, casualtyPts, areaPts, score, level);
    }
    /** Highest score first (PriorityQueue ordering). */
    @Override public int compareTo(Disaster o) {
        int c = Double.compare(o.score, this.score);
        return c != 0 ? c : Integer.compare(this.id, o.id);
    }
}

/** Notifier interface: separate alerts for authorities, responders and the public (Module 3). */
interface Notifier {
    void alertAuthorities(Disaster d);
    void alertResponders(Disaster d);
    void alertPublic(Disaster d);
}

/** Stock tracking and allocation (Module 4). */
class ResourceManager {
    final Map<String, Integer> stock = new LinkedHashMap<>();
    ResourceManager(int teams, int ambulances) { setStock(teams, ambulances); }
    void setStock(int teams, int ambulances) {
        stock.put("Rescue teams", teams);
        stock.put("Ambulances", ambulances);
    }
    int teamsNeeded(Disaster d)      { return (int) Math.ceil(d.score / 20.0); }
    int ambulancesNeeded(Disaster d) { return (int) Math.ceil(d.casualties / 18.0); }

    void allocate(String res, int needed) throws ShortageException {
        int have = stock.get(res);
        if (needed > have)
            throw new ShortageException(res + ": needed " + needed + ", only " + have + " left");
        stock.put(res, have - needed);
    }
}

public class DisasterManagementSystem extends JFrame {

    // ---------- Colours / fonts ----------
    static final Color NAVY = new Color(0x0B2A5B), ORANGE = new Color(0xE67E22),
            BG = new Color(0xF3F5F9), GREEN = new Color(0x1E8E5A), RED = new Color(0xC0392B),
            GREY = new Color(0x5F6B7A);
    static final Font BASE = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    // ---------- Data (in-memory central database) ----------
    final List<Disaster> database = new ArrayList<>();
    final PriorityQueue<Disaster> queue = new PriorityQueue<>();
    ResourceManager resources = new ResourceManager(10, 12);
    int initTeams = 10, initAmb = 12;

    // ---------- UI components ----------
    final CardLayout cards = new CardLayout();
    final JPanel content = new JPanel(cards);
    final Map<String, JButton> navButtons = new LinkedHashMap<>();

    JComboBox<String> typeBox; JTextField locField, casField, areaField; JSpinner intSpin;
    DefaultTableModel incModel, sevModel, queueModel, respModel, demandModel;
    JTable sevTable;
    JTextArea explainArea, alertLog, resLog, respLog, reportArea;
    JLabel statTotal, statQueued, statResolved, teamsLbl, ambLbl, demandLbl;
    JProgressBar teamsBar, ambBar;
    JSpinner teamsSpin, ambSpin;

    final Notifier notifier = new Notifier() {
        public void alertAuthorities(Disaster d) { alertLog.append("  [AUTHORITIES] " + d.level + " " + d.label() + "\n"); }
        public void alertResponders(Disaster d)  { alertLog.append("  [RESPONDERS]  Deploy to " + d.location + ", expected casualties: " + d.casualties + "\n"); }
        public void alertPublic(Disaster d) {
            if (d.level == Level.LOW) alertLog.append("  [PUBLIC]      Skipped (Low severity)\n");
            else alertLog.append("  [PUBLIC]      Warning issued for " + d.location + "\n");
        }
    };

    public DisasterManagementSystem() {
        super("Disaster Management System - CGB1201 Java Programming");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 720); setMinimumSize(new Dimension(1000, 620));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);

        content.setBackground(BG);
        content.add(buildHome(), "Home");
        content.add(buildDetection(), "1. Detection");
        content.add(buildSeverity(), "2. Severity");
        content.add(buildAlerts(), "3. Alerts");
        content.add(buildResources(), "4. Resources");
        content.add(buildResponse(), "5. Response");
        add(content, BorderLayout.CENTER);
        show("Home");
    }

    // ================= Navigation =================
    JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(NAVY);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(220, 0));
        side.setBorder(new EmptyBorder(18, 12, 18, 12));
        JLabel t = new JLabel("<html><b>DISASTER<br>MANAGEMENT<br>SYSTEM</b></html>");
        t.setForeground(Color.WHITE); t.setFont(new Font("Segoe UI", Font.BOLD, 18));
        t.setAlignmentX(0f);
        side.add(t); side.add(Box.createVerticalStrut(24));
        for (String n : new String[]{"Home", "1. Detection", "2. Severity", "3. Alerts", "4. Resources", "5. Response"}) {
            JButton b = new JButton(n);
            b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setForeground(Color.WHITE); b.setBackground(NAVY);
            b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            b.setBorder(new EmptyBorder(8, 14, 8, 14));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setAlignmentX(0f);
            b.addActionListener(e -> show(n));
            navButtons.put(n, b);
            side.add(b); side.add(Box.createVerticalStrut(4));
        }
        side.add(Box.createVerticalGlue());
        JButton reset = button("Reset System", RED, this::resetSystem);
        reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38)); reset.setAlignmentX(0f);
        side.add(reset);
        return side;
    }
    void show(String name) {
        cards.show(content, name);
        navButtons.forEach((k, b) -> b.setBackground(k.equals(name) ? ORANGE : NAVY));
        refreshAll();
    }

    // ================= Helpers =================
    JButton button(String text, Color bg, Runnable r) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true);
        b.setBorder(new EmptyBorder(9, 16, 9, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> r.run());
        return b;
    }
    JPanel page(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(BG); p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel h = new JPanel(new GridLayout(2, 1)); h.setOpaque(false);
        JLabel a = new JLabel(title); a.setFont(new Font("Segoe UI", Font.BOLD, 24)); a.setForeground(NAVY);
        JLabel b = new JLabel(subtitle); b.setFont(BASE); b.setForeground(GREY);
        h.add(a); h.add(b); p.add(h, BorderLayout.NORTH);
        return p;
    }
    JPanel card() {
        JPanel c = new JPanel(); c.setBackground(Color.WHITE);
        c.setBorder(new CompoundBorder(new LineBorder(new Color(0xDDE2EA)), new EmptyBorder(14, 16, 14, 16)));
        return c;
    }
    JTextArea log() {
        JTextArea a = new JTextArea(); a.setEditable(false); a.setFont(MONO);
        a.setMargin(new Insets(8, 10, 8, 10)); return a;
    }
    JScrollPane scroll(Component c, String title) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(new TitledBorder(new LineBorder(new Color(0xDDE2EA)), title, TitledBorder.LEFT,
                TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), NAVY));
        return s;
    }
    DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r, int c) { return false; } };
    }
    JTable table(DefaultTableModel m) {
        JTable t = new JTable(m); t.setRowHeight(26); t.setFont(BASE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.getTableHeader().setBackground(NAVY); t.getTableHeader().setForeground(Color.WHITE);
        t.setSelectionBackground(new Color(0xFAD7A8)); t.setSelectionForeground(Color.BLACK);
        t.setGridColor(new Color(0xE6E9EF));
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(tb, v, s, f, row, col);
                setHorizontalAlignment(CENTER);
                if (!s) { setForeground(Color.BLACK); setFont(BASE); }
                String x = String.valueOf(v);
                if (!s && (x.equals("CRITICAL") || x.equals("HIGH") || x.equals("MODERATE") || x.equals("LOW") || x.equals("RESOLVED"))) {
                    setFont(BASE.deriveFont(Font.BOLD));
                    setForeground(x.equals("CRITICAL") ? RED : x.equals("HIGH") ? ORANGE
                            : x.equals("MODERATE") ? new Color(0xB7950B) : GREEN);
                }
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, r);
        return t;
    }
    void warn(String m) { JOptionPane.showMessageDialog(this, m, "Validation Error", JOptionPane.WARNING_MESSAGE); }
    void info(String m) { JOptionPane.showMessageDialog(this, m, "Information", JOptionPane.INFORMATION_MESSAGE); }

    // ================= Home =================
    JPanel buildHome() {
        JPanel p = page("Disaster Management System",
                "Collects disaster data, assesses severity, alerts, allocates resources and tracks recovery");
        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0)); stats.setOpaque(false);
        statTotal = statCard(stats, "Total Incidents"); statQueued = statCard(stats, "Waiting in Priority Queue");
        statResolved = statCard(stats, "Resolved");

        JPanel flow = new JPanel(new GridLayout(1, 5, 10, 0)); flow.setOpaque(false);
        String[][] m = {{"1. Detection & Data", "Simulated sensor data"}, {"2. Severity & Priority", "Score out of 100, PriorityQueue"},
                {"3. Alerts & Notice", "Notifier interface, 3 recipients"}, {"4. Resource Mgmt", "Stock tracking & allocation"},
                {"5. Response & Recovery", "Status tracking to Resolved"}};
        for (String[] x : m) {
            JPanel c = card(); c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            JLabel a = new JLabel("<html><b>" + x[0] + "</b></html>"); a.setForeground(NAVY); a.setFont(BASE);
            JLabel b = new JLabel("<html><div style='width:120px'>" + x[1] + "</div></html>"); b.setForeground(GREY); b.setFont(BASE);
            c.add(a); c.add(Box.createVerticalStrut(6)); c.add(b); flow.add(c);
        }
        JTextArea how = log(); how.setFont(BASE); how.setLineWrap(true); how.setWrapStyleWord(true);
        how.setText("HOW TO DEMONSTRATE\n\n"
                + "1. Open '1. Detection' and click 'Load Sample Data' (or add your own incident).\n"
                + "2. Open '2. Severity' and click 'Assess & Prioritize' to see the score breakdown and queue order.\n"
                + "3. Open '5. Response' and click 'Process Next' (or 'Process All'). Alerts, resource allocation and\n"
                + "    status changes are generated for the highest-scored incident first.\n"
                + "4. Check '3. Alerts' and '4. Resources', then click 'Generate Final Report'.\n\n"
                + "SEVERITY LEVELS:  LOW < 25  |  MODERATE 25-49.9  |  HIGH 50-74.9  |  CRITICAL >= 75");
        JPanel centre = new JPanel(new BorderLayout(0, 14)); centre.setOpaque(false);
        centre.add(stats, BorderLayout.NORTH); centre.add(flow, BorderLayout.CENTER);
        JPanel wrap = new JPanel(new BorderLayout(0, 14)); wrap.setOpaque(false);
        wrap.add(centre, BorderLayout.NORTH); wrap.add(scroll(how, "Quick Guide"), BorderLayout.CENTER);
        p.add(wrap, BorderLayout.CENTER);
        return p;
    }
    JLabel statCard(JPanel parent, String title) {
        JPanel c = card(); c.setLayout(new GridLayout(2, 1));
        JLabel v = new JLabel("0"); v.setFont(new Font("Segoe UI", Font.BOLD, 30)); v.setForeground(ORANGE);
        JLabel t = new JLabel(title); t.setForeground(GREY); t.setFont(BASE);
        c.add(v); c.add(t); parent.add(c); return v;
    }

    // ================= Module 1: Detection =================
    JPanel buildDetection() {
        JPanel p = page("Module 1 - Disaster Detection & Data Collection",
                "Enter simulated sensor data; every incident is saved in the in-memory central database");
        JPanel form = card(); form.setLayout(new GridBagLayout()); form.setPreferredSize(new Dimension(300, 0));
        typeBox = new JComboBox<>(new String[]{"Flood", "Earthquake", "Fire", "Cyclone"});
        locField = new JTextField(); casField = new JTextField(); areaField = new JTextField();
        intSpin = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
        Object[][] rows = {{"Disaster type", typeBox}, {"Location", locField}, {"Intensity (1-10)", intSpin},
                {"Casualties", casField}, {"Affected area (sq km)", areaField}};
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(3, 0, 3, 0);
        int y = 0;
        for (Object[] r : rows) {
            g.gridy = y++; JLabel l = new JLabel((String) r[0]); l.setFont(BASE); l.setForeground(GREY); form.add(l, g);
            g.gridy = y++; Component c = (Component) r[1]; c.setFont(BASE);
            c.setPreferredSize(new Dimension(200, 30)); form.add(c, g);
        }
        g.gridy = y++; g.insets = new Insets(14, 0, 3, 0); form.add(button("Add Incident", GREEN, this::addIncident), g);
        g.gridy = y++; g.insets = new Insets(3, 0, 3, 0); form.add(button("Load Sample Data", NAVY, this::loadSample), g);
        g.gridy = y++; form.add(button("Clear All Incidents", RED, this::clearIncidents), g);
        g.gridy = y; g.weighty = 1; form.add(new JLabel(), g);

        incModel = model("ID", "Type", "Location", "Intensity", "Casualties", "Area (sq km)", "Status");
        JPanel body = new JPanel(new BorderLayout(14, 0)); body.setOpaque(false);
        body.add(form, BorderLayout.WEST);
        body.add(scroll(table(incModel), "Central Database (all incidents)"), BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }
    void addIncident() {
        String loc = locField.getText().trim();
        if (loc.isEmpty()) { warn("Location cannot be empty."); return; }
        if (!loc.matches("[A-Za-z][A-Za-z .'-]*")) { warn("Location must contain letters only (e.g. Chennai)."); return; }
        int cas; double area;
        try { cas = Integer.parseInt(casField.getText().trim()); }
        catch (NumberFormatException e) { warn("Casualties must be a whole number (e.g. 40)."); return; }
        if (cas < 0) { warn("Casualties cannot be negative."); return; }
        try { area = Double.parseDouble(areaField.getText().trim()); }
        catch (NumberFormatException e) { warn("Affected area must be a number (e.g. 120)."); return; }
        if (area <= 0 || area > 100000) { warn("Affected area must be greater than 0 and at most 100000 sq km."); return; }
        Disaster d = new Disaster((String) typeBox.getSelectedItem(), loc, (Integer) intSpin.getValue(), cas, area);
        database.add(d);
        locField.setText(""); casField.setText(""); areaField.setText("");
        refreshAll();
    }
    void loadSample() {
        if (!database.isEmpty() && JOptionPane.showConfirmDialog(this,
                "Sample data will be added to the existing incidents. Continue?", "Load Sample Data",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        database.add(new Disaster("Flood", "Chennai", 7, 40, 120));
        database.add(new Disaster("Earthquake", "Bhuj", 8, 180, 300));
        database.add(new Disaster("Fire", "Mumbai", 6, 12, 2));
        database.add(new Disaster("Cyclone", "Odisha", 8, 60, 400));
        refreshAll();
    }
    void clearIncidents() {
        if (database.isEmpty()) { info("There are no incidents to clear."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete all incidents and the queue?", "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            database.clear(); queue.clear(); Disaster.resetCounter(); refreshAll();
        }
    }

    // ================= Module 2: Severity =================
    JPanel buildSeverity() {
        JPanel p = page("Module 2 - Severity Assessment & Prioritization",
                "Score out of 100: intensity (40) + casualties (30) + area (30). Highest score is served first");
        sevModel = model("ID", "Incident", "Intensity /40", "Casualties /30", "Area /30", "Score /100", "Level");
        sevTable = table(sevModel);
        explainArea = log(); explainArea.setLineWrap(true); explainArea.setWrapStyleWord(true); explainArea.setText("Select an incident above to see its explainable score breakdown.");
        queueModel = model("Rank", "Incident", "Score", "Level");
        sevTable.getSelectionModel().addListSelectionListener(e -> {
            int r = sevTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                Disaster d = find((Integer) sevModel.getValueAt(r, 0));
                explainArea.setText(d == null ? "" : d.label() + "\n" + (d.assessed ? d.breakdown()
                        : "Not assessed yet. Click 'Assess & Prioritize'."));
            }
        });
        JPanel top = new JPanel(new BorderLayout(0, 8)); top.setOpaque(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); bar.setOpaque(false);
        bar.add(button("Assess & Prioritize", ORANGE, this::assessAll));
        JScrollPane sevScroll = scroll(sevTable, "Severity scores (select a row to see its explainable breakdown)");
        sevScroll.setPreferredSize(new Dimension(0, 230));
        top.add(bar, BorderLayout.NORTH); top.add(sevScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new GridLayout(1, 2, 14, 0)); bottom.setOpaque(false);
        bottom.add(scroll(explainArea, "Explainable Score Breakdown"));
        bottom.add(scroll(table(queueModel), "Priority Queue (highest score first)"));
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        body.add(top, BorderLayout.NORTH); body.add(bottom, BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }
    /** Calculates every score and (re)builds the PriorityQueue. */
    void assessAll() {
        if (database.isEmpty()) {
            info("No incidents to assess. Add incidents or load sample data in '1. Detection'.");
            return;
        }
        queue.clear();
        for (Disaster d : database) {
            d.assess();
            if (d.status != Status.RESOLVED) queue.add(d);
        }
        refreshAll();
    }

    // ================= Module 3: Alerts =================
    JPanel buildAlerts() {
        JPanel p = page("Module 3 - Alert & Notification",
                "Notifier interface sends separate alerts to authorities, responders and the public");
        alertLog = log();
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); bar.setOpaque(false);
        bar.add(button("Clear Alert Log", NAVY, () -> alertLog.setText("")));
        JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH);
        body.add(scroll(alertLog, "Alert Log (generated when an incident is picked from the queue)"), BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    // ================= Module 4: Resources =================
    JPanel buildResources() {
        JPanel p = page("Module 4 - Resource Management",
                "Stock of rescue teams and ambulances; a ShortageException is raised when stock is not enough");
        teamsLbl = new JLabel(); ambLbl = new JLabel();
        teamsLbl.setFont(BASE); ambLbl.setFont(BASE);
        teamsBar = new JProgressBar(0, initTeams); ambBar = new JProgressBar(0, initAmb);
        for (JProgressBar b : new JProgressBar[]{teamsBar, ambBar}) { b.setStringPainted(true); b.setForeground(GREEN); }
        teamsSpin = new JSpinner(new SpinnerNumberModel(initTeams, 0, 1000, 1));
        ambSpin = new JSpinner(new SpinnerNumberModel(initAmb, 0, 1000, 1));

        JPanel set = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); set.setBackground(Color.WHITE);
        set.add(new JLabel("Rescue teams:")); set.add(teamsSpin);
        set.add(new JLabel("Ambulances:")); set.add(ambSpin);
        set.add(button("Set Stock", NAVY, this::applyStock));

        JPanel stock = card(); stock.setLayout(new GridLayout(0, 1, 0, 6));
        stock.add(teamsLbl); stock.add(teamsBar); stock.add(ambLbl); stock.add(ambBar); stock.add(set);

        resLog = log();
        demandModel = model("Rank", "Incident", "Score", "Teams needed", "Ambulances needed");
        demandLbl = new JLabel(" "); demandLbl.setFont(BASE.deriveFont(Font.BOLD));
        JPanel demand = new JPanel(new BorderLayout(0, 6)); demand.setOpaque(false);
        demand.add(scroll(table(demandModel), "Resource Demand (pending incidents, in processing order)"), BorderLayout.CENTER);
        JScrollPane logScroll = scroll(resLog, "Allocation Log");
        logScroll.setPreferredSize(new Dimension(380, 0));
        JPanel centre = new JPanel(new BorderLayout(14, 0)); centre.setOpaque(false);
        centre.add(demand, BorderLayout.CENTER);
        centre.add(logScroll, BorderLayout.EAST);
        JPanel north = new JPanel(new BorderLayout(0, 8)); north.setOpaque(false);
        north.add(stock, BorderLayout.NORTH);
        north.add(demandLbl, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(centre, BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }
    void applyStock() {
        initTeams = (Integer) teamsSpin.getValue();
        initAmb = (Integer) ambSpin.getValue();
        resources.setStock(initTeams, initAmb);
        info("Stock updated: " + initTeams + " rescue teams, " + initAmb + " ambulances.");
        refreshAll();
    }

    // ================= Module 5: Response & Recovery =================
    JPanel buildResponse() {
        JPanel p = page("Module 5 - Response & Recovery",
                "Reported -> Assigned -> In Progress -> Resolved. The highest-scored incident is processed first");
        respModel = model("ID", "Incident", "Level", "Status");
        respLog = log(); reportArea = log();
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); bar.setOpaque(false);
        bar.add(button("Process Next", GREEN, this::processNext));
        bar.add(button("Process All", ORANGE, this::processAll));
        bar.add(button("Generate Final Report", NAVY, this::generateReport));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 10)); left.setOpaque(false);
        left.add(scroll(table(respModel), "Incident Status"));
        left.add(scroll(respLog, "Status Changes"));
        JPanel centre = new JPanel(new GridLayout(1, 2, 14, 0)); centre.setOpaque(false);
        centre.add(left);
        centre.add(scroll(reportArea, "Final Report"));

        JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH); body.add(centre, BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    /** Returns true if there is something in the queue to process; otherwise tells the user why not. */
    boolean readyToProcess() {
        if (database.isEmpty()) { info("No incidents. Load sample data in '1. Detection' first."); return false; }
        if (queue.isEmpty()) {
            boolean unassessed = false;
            for (Disaster d : database) if (!d.assessed) unassessed = true;
            info(unassessed ? "Open '2. Severity' and click 'Assess & Prioritize' first."
                            : "All incidents have already been resolved.");
            return false;
        }
        return true;
    }
    void processNext() {
        if (!readyToProcess()) return;
        process(queue.poll());
        refreshAll();
    }
    void processAll() {
        if (!readyToProcess()) return;
        while (!queue.isEmpty()) process(queue.poll());
        refreshAll();
    }

    /** Modules 3, 4 and 5 for one incident taken from the head of the queue. */
    void process(Disaster d) {
        String head = "=== PROCESSING " + d.level + " " + d.label() + " (score " + d.score + ") ===\n";

        alertLog.append(head);
        notifier.alertAuthorities(d);
        notifier.alertResponders(d);
        notifier.alertPublic(d);
        alertLog.append("\n");

        resLog.append(head);
        try {
            int t = resources.teamsNeeded(d);
            resources.allocate("Rescue teams", t);
            resLog.append("  [OK] Rescue teams: " + t + " allocated\n");
            int a = resources.ambulancesNeeded(d);
            resources.allocate("Ambulances", a);
            resLog.append("  [OK] Ambulances: " + a + " allocated\n");
        } catch (ShortageException e) {
            resLog.append("  [SHORTAGE] " + e.getMessage() + "\n");
        }
        resLog.append("\n");

        respLog.append(head);
        for (Status s : new Status[]{Status.ASSIGNED, Status.IN_PROGRESS, Status.RESOLVED}) {
            d.status = s;
            respLog.append("  Status -> " + s + "\n");
        }
        respLog.append("\n");
    }

    void generateReport() {
        if (database.isEmpty()) { info("There are no incidents to report."); return; }
        StringBuilder sb = new StringBuilder("FINAL REPORT\n");
        for (Disaster d : database)
            sb.append("  ").append(d.label()).append(" | ")
              .append(d.assessed ? d.level.toString() : "NOT ASSESSED").append(" | ")
              .append(d.status).append("\n");
        sb.append("  Remaining stock: ").append(resources.stock).append("\n");
        reportArea.setText(sb.toString());
    }

    void resetSystem() {
        if (JOptionPane.showConfirmDialog(this, "Reset the whole system (incidents, queue, stock and logs)?",
                "Confirm Reset", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        database.clear(); queue.clear(); Disaster.resetCounter();
        resources.setStock(initTeams, initAmb);
        alertLog.setText(""); resLog.setText(""); respLog.setText(""); reportArea.setText("");
        explainArea.setText("Select an incident above to see its explainable score breakdown.");
        refreshAll();
    }

    // ================= Refresh all screens =================
    Disaster find(int id) {
        for (Disaster d : database) if (d.id == id) return d;
        return null;
    }
    static String num(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v); }

    void refreshAll() {
        incModel.setRowCount(0); sevModel.setRowCount(0); queueModel.setRowCount(0); respModel.setRowCount(0);
        int resolved = 0;
        for (Disaster d : database) {
            incModel.addRow(new Object[]{d.id, d.type, d.location, d.intensity, d.casualties, num(d.area), d.status});
            if (d.assessed)
                sevModel.addRow(new Object[]{d.id, d.label(), String.format("%.1f", d.intensityPts),
                        String.format("%.1f", d.casualtyPts), String.format("%.1f", d.areaPts),
                        String.format("%.1f", d.score), d.level});
            else
                sevModel.addRow(new Object[]{d.id, d.label(), "-", "-", "-", "-", "-"});
            respModel.addRow(new Object[]{d.id, d.label(), d.assessed ? d.level : "-", d.status});
            if (d.status == Status.RESOLVED) resolved++;
        }
        List<Disaster> ordered = new ArrayList<>(queue);
        Collections.sort(ordered);
        int rank = 1;
        for (Disaster d : ordered) queueModel.addRow(new Object[]{rank++, d.label(), d.score, d.level});

        // Resource demand of the pending incidents (same formulas as ResourceManager)
        demandModel.setRowCount(0);
        int needTeams = 0, needAmb = 0; rank = 1;
        for (Disaster d : ordered) {
            int t = resources.teamsNeeded(d), a = resources.ambulancesNeeded(d);
            demandModel.addRow(new Object[]{rank++, d.label(), d.score, t, a});
            needTeams += t; needAmb += a;
        }

        statTotal.setText(String.valueOf(database.size()));
        statQueued.setText(String.valueOf(queue.size()));
        statResolved.setText(String.valueOf(resolved));

        int teams = resources.stock.get("Rescue teams"), amb = resources.stock.get("Ambulances");
        teamsLbl.setText("Rescue teams available: " + teams + " / " + initTeams);
        ambLbl.setText("Ambulances available: " + amb + " / " + initAmb);
        teamsBar.setMaximum(Math.max(initTeams, 1)); teamsBar.setValue(Math.min(teams, Math.max(initTeams, 1)));
        ambBar.setMaximum(Math.max(initAmb, 1));     ambBar.setValue(Math.min(amb, Math.max(initAmb, 1)));
        teamsBar.setString(teams + " / " + initTeams); ambBar.setString(amb + " / " + initAmb);

        if (ordered.isEmpty()) {
            demandLbl.setForeground(GREY);
            demandLbl.setText("No pending incidents. Load data and click 'Assess & Prioritize' to see the demand.");
        } else {
            int shortT = Math.max(0, needTeams - teams), shortA = Math.max(0, needAmb - amb);
            String total = "Total needed: " + needTeams + " rescue teams, " + needAmb + " ambulances.  ";
            if (shortT == 0 && shortA == 0) {
                demandLbl.setForeground(GREEN);
                demandLbl.setText(total + "Current stock is enough.");
            } else {
                demandLbl.setForeground(RED);
                demandLbl.setText(total + "Short by " + shortT + " teams, " + shortA + " ambulances. Add stock or expect shortages.");
            }
        }
    }

    // ================= Main =================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DisasterManagementSystem().setVisible(true));
    }
}
