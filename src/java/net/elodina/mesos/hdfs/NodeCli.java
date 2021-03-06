package net.elodina.mesos.hdfs;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.elodina.mesos.util.Strings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static net.elodina.mesos.hdfs.Cli.Error;
import static net.elodina.mesos.hdfs.Cli.*;

public class NodeCli {
    public static void handle(List<String> args, boolean help) {
        if (help) {
            handleHelp(args);
            return;
        }

        if (args.isEmpty()) throw new Error("command required");
        String cmd = args.remove(0);

        switch (cmd) {
            case "list": handleList(args, false); break;
            case "add": case "update": handleAddUpdate(cmd, args, false); break;
            case "start": case "stop": handleStartStop(cmd, args, false); break;
            case "remove": handleRemove(args, false); break;
            default: throw new Error("unsupported command " + cmd);
        }
    }

    private static void handleHelp(List<String> args) {
        String cmd = args.isEmpty() ? null : args.remove(0);

        if (cmd == null) {
            printLine("Node management commands\nUsage: node <cmd>\n");
            printCmds();

            printLine();
            printLine("Run `help node <cmd>` to see details of specific command");
            return;
        }

        switch (cmd) {
            case "list": handleList(args, true); break;
            case "add": case "update": handleAddUpdate(cmd, args, true); break;
            case "start": case "stop": handleStartStop(cmd, args, true); break;
            case "remove": handleRemove(args, true); break;
            default: throw new Error("unsupported command " + cmd);
        }
    }

    private static void handleList(List<String> args, boolean help) {
        if (help) {
            printLine("List nodes\nUsage: node list [<ids>]\n");
            handleGenericOptions(null, true);
            return;
        }

        String expr = !args.isEmpty() ? args.remove(0) : null;

        Map<String, String> params = new HashMap<>();
        if (expr != null) params.put("node", expr);

        JSONArray json;
        try { json = sendRequest("/node/list", params); }
        catch (IOException e) { throw new Error("" + e); }

        List<Node> nodes = Node.fromJsonArray(json);
        String title = nodes.isEmpty() ? "no nodes" : "node" + (nodes.size() > 1 ? "s" : "") + ":";
        printLine(title);

        for (Node node : nodes) {
            printNode(node, 1);
            printLine();
        }
    }

    private static void handleAddUpdate(String cmd, List<String> args, boolean help) {
        OptionParser parser = new OptionParser();
        if (cmd.equals("add")) parser.accepts("type", "node type (name_node, data_node).").withRequiredArg().required().ofType(String.class);

        parser.accepts("cpus", "CPU amount (0.5, 1, 2).").withRequiredArg().ofType(Double.class);
        parser.accepts("mem", "Mem amount in Mb.").withRequiredArg().ofType(Long.class);

        parser.accepts("constraints", "Node constraints (hostname=like:master,rack=like:1.*)").withRequiredArg();

        parser.accepts("executor-jvm-opts", "Executor JVM options.").withRequiredArg().ofType(String.class);
        parser.accepts("hadoop-jvm-opts", "Hadoop JVM options.").withRequiredArg().ofType(String.class);

        parser.accepts("core-site-opts", "Hadoop core-site.xml options.").withRequiredArg().ofType(String.class);
        parser.accepts("hdfs-site-opts", "Hadoop hdfs-site.xml options.").withRequiredArg().ofType(String.class);

        parser.accepts("external-fs-uri", "FS URI of external namenode. If defined this node becomes external.").withRequiredArg().ofType(String.class);

        parser.accepts("failover-delay", "failover delay (10s, 5m, 3h)").withRequiredArg().ofType(String.class);
        parser.accepts("failover-max-delay", "max failover delay. See failoverDelay.").withRequiredArg().ofType(String.class);
        parser.accepts("failover-max-tries", "max failover tries. Default - none").withRequiredArg().ofType(String.class);

        if (help) {
            printLine(Strings.capitalize(cmd) + " node \nUsage: node " + cmd + " <ids> [options]\n");
            try { parser.printHelpOn(out); }
            catch (IOException ignore) {}

            printLine();
            handleGenericOptions(args, true);
            return;
        }

        if (args.isEmpty()) throw new Error("id required");
        String expr = args.remove(0);

        OptionSet options;
        try { options = parser.parse(args.toArray(new String[args.size()])); }
        catch (OptionException e) {
            try { parser.printHelpOn(out); }
            catch (IOException ignore) {}

            printLine();
            throw new Error(e.getMessage());
        }

        String type = (String) options.valueOf("type");
        Double cpus = (Double) options.valueOf("cpus");
        Long mem = (Long) options.valueOf("mem");

        String constraints = (String) options.valueOf("constraints");

        String executorJvmOpts = (String) options.valueOf("executor-jvm-opts");
        String hadoopJvmOpts = (String) options.valueOf("hadoop-jvm-opts");

        String coreSiteOpts = (String) options.valueOf("core-site-opts");
        String hdfsSiteOpts = (String) options.valueOf("hdfs-site-opts");

        String externalFsUri = (String) options.valueOf("external-fs-uri");

        String failoverDelay = (String) options.valueOf("failover-delay");
        String failoverMaxDelay = (String) options.valueOf("failover-max-delay");
        String failoverMaxTries = (String) options.valueOf("failover-max-tries");

        Map<String, String> params = new HashMap<>();
        params.put("node", expr);

        if (type != null) params.put("type", type);
        if (cpus != null) params.put("cpus", "" + cpus);
        if (mem != null) params.put("mem", "" + mem);

        if (constraints != null) params.put("constraints", constraints);

        if (executorJvmOpts != null) params.put("executorJvmOpts", executorJvmOpts);
        if (hadoopJvmOpts != null) params.put("hadoopJvmOpts", hadoopJvmOpts);

        if (coreSiteOpts != null) params.put("coreSiteOpts", coreSiteOpts);
        if (hdfsSiteOpts != null) params.put("hdfsSiteOpts", hdfsSiteOpts);

        if (externalFsUri != null) params.put("externalFsUri", externalFsUri);

        if (failoverDelay != null) params.put("failoverDelay", failoverDelay);
        if (failoverMaxDelay != null) params.put("failoverMaxDelay", failoverMaxDelay);
        if (failoverMaxTries != null) params.put("failoverMaxTries", failoverMaxTries);

        JSONArray json;
        try { json = sendRequest("/node/" + cmd, params); }
        catch (IOException e) { throw new Error("" + e); }

        List<Node> nodes = Node.fromJsonArray(json);
        String title = "node" + (nodes.size() > 1 ? "s" : "") + (cmd.equals("add") ? " added" : " updated") + ":";
        printLine(title);

        for (Node node : nodes) {
            printNode(node, 1);
            printLine();
        }
    }

    private static void handleStartStop(String cmd, List<String> args, boolean help) {
        OptionParser parser = new OptionParser();
        parser.accepts("timeout", "timeout (30s, 1m, 1h). 0s - no timeout").withRequiredArg().ofType(String.class);

        if (help) {
            printLine(Strings.capitalize(cmd) + " node \nUsage: node " + cmd + " <ids> [options]\n");
            try { parser.printHelpOn(out); }
            catch (IOException ignore) {}

            printLine();
            handleGenericOptions(args, true);
            return;
        }

        if (args.isEmpty()) throw new Error("id required");
        String expr = args.remove(0);

        OptionSet options;
        try { options = parser.parse(args.toArray(new String[args.size()])); }
        catch (OptionException e) {
            try { parser.printHelpOn(out); }
            catch (IOException ignore) {}

            printLine();
            throw new Error(e.getMessage());
        }

        String timeout = (String) options.valueOf("timeout");
        Boolean force = (Boolean) options.valueOf("force");

        HashMap<String, String> params = new HashMap<>();
        params.put("node", expr);
        if (timeout != null) params.put("timeout", timeout);
        if (force != null) params.put("force", "" + force);

        JSONObject json;
        try { json = sendRequest("/node/" + cmd, params); }
        catch (IOException e) { throw new Error("" + e); }

        String status = "" + json.get("status");
        List<Node> nodes = Node.fromJsonArray((JSONArray) json.get("nodes"));

        String title = nodes.size() > 1 ? "nodes " : "node ";
        switch (status) {
            case "started": case "stopped": title += status + ":"; break;
            case "scheduled": title += status + " to " + cmd +  ":"; break;
            case "timeout":  throw new Error(cmd + " timeout");
        }

        printLine(title);
        for (Node node : nodes) {
            printNode(node, 1);
            printLine();
        }
    }

    private static void handleRemove(List<String> args, boolean help) {
        if (help) {
            printLine("Remove node\nUsage: node remove <ids>\n");
            handleGenericOptions(null, true);
            return;
        }

        if (args.isEmpty()) throw new Error("id required");
        String expr = args.remove(0);

        JSONArray json;
        try { json = sendRequest("/node/remove", Collections.singletonMap("node", expr)); }
        catch (IOException e) { throw new Error("" + e); }

        String title = json.size() == 1 ? "node " + json.get(0) : "nodes " + Strings.join(json, ", ");
        title += " removed";

        printLine(title);
    }

    private static void printNode(Node node, int indent) {
        printLine("id: " + node.id, indent);
        printLine("type: " + node.type.name().toLowerCase() + (node.isExternal() ? " (external)" : ""), indent);

        if (node.isExternal()) {
            printLine("external-fs-uri: " + node.externalFsUri, indent);
            return;
        }

        printLine("state: " + nodeState(node), indent);
        printLine("resources: " + nodeResources(node), indent);

        if (!node.constraints.isEmpty()) printLine("constraints: " + Strings.formatMap(node.constraints), indent);

        if (node.executorJvmOpts != null) printLine("executor-jvm-opts: " + node.executorJvmOpts, indent);
        if (node.hadoopJvmOpts != null) printLine("hadoop-jvm-opts: " + node.hadoopJvmOpts, indent);

        if (!node.coreSiteOpts.isEmpty()) printLine("core-site-opts: " + Strings.formatMap(node.coreSiteOpts), indent);
        if (!node.hdfsSiteOpts.isEmpty()) printLine("hdfs-site-opts: " + Strings.formatMap(node.hdfsSiteOpts), indent);

        printLine("stickiness: " + nodeStickiness(node.stickiness), indent);
        printLine("failover: " + nodeFailover(node.failover), indent);
        if (node.reservation != null) printLine("reservation: " + nodeReservation(node.reservation), indent);
        if (node.runtime != null) printNodeRuntime(node.runtime, indent);
    }

    private static void printNodeRuntime(Node.Runtime runtime, int indent) {
        printLine("runtime:", indent);
        printLine("task: " + runtime.taskId, indent + 1);
        printLine("executor: " + runtime.executorId, indent + 1);
        printLine("slave: " + runtime.slaveId + " (" + runtime.hostname + ")", indent + 1);
    }

    private static void printCmds() {
        printLine("Commands:");
        printLine("list       - list nodes", 1);
        printLine("add        - add node", 1);
        printLine("update     - update node", 1);
        printLine("start      - start node", 1);
        printLine("stop       - stop node", 1);
        printLine("remove     - remove node", 1);
    }

    private static String nodeState(Node node) {
        if (node.state != Node.State.STARTING) return "" + node.state.name().toLowerCase();

        if (node.failover.isWaitingDelay(new Date())) {
            String s = "failed " + node.failover.failures;
            if (node.failover.maxTries != null) s += "/" + node.failover.maxTries;
            s += " " + time(node.failover.failureTime);
            s += ", next start " + time(node.failover.delayExpires());
            return s;
        }

        if (node.failover.failures > 0) {
            String s = "starting " + (node.failover.failures + 1);
            if (node.failover.maxTries != null) s += "/" + node.failover.maxTries;
            s += ", failed " + time(node.failover.failureTime);
            return s;
        }

        return "" + Node.State.STARTING.name().toLowerCase();
    }

    private static String nodeStickiness(Node.Stickiness stickiness) {
        String s = "period:" + stickiness.period;

        if (stickiness.hostname != null) s += ", hostname:" + stickiness.hostname;
        if (stickiness.stopTime != null) s += ", expires:" + time(stickiness.expires());

        return s;
    }

    private static String nodeFailover(Node.Failover failover) {
        String s = "delay:" + failover.delay;

        s += ", max-delay:" + failover.maxDelay;
        if (failover.maxTries != null) s += ", max-tries:" + failover.maxTries;

        return s;
    }

    private static String nodeResources(Node node) {
        String s = "";

        s += "cpus:" + node.cpus;
        s += ", mem:" + node.mem;

        return s;
    }

    private static String nodeReservation(Node.Reservation reservation) {
        String s = "";

        s += "cpus:" + reservation.cpus;
        s += ", mem:" + reservation.mem;
        s += ", ports:" + Strings.formatMap(reservation.ports);

        return s;
    }

    public static String dateTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX").format(date);
    }

    public static String time(Date date) {
        return new SimpleDateFormat("HH:mm:ssX").format(date);
    }
}
