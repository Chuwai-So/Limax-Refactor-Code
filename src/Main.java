import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Entities
record Article(String name) {}

record Farmer(String name) {}

record Schedule(Article article, String date) {}

class InventoryItem {
    private final Article article;
    private int quantity;

    InventoryItem(Article article, int quantity) {
        this.article = article;
        this.quantity = quantity;
    }

    void add(int amount) {
        this.quantity += amount;
    }

    void subtract(int amount) {
        this.quantity -= amount;
    }

    int getQuantity() {
        return quantity;
    }
}

/*-----------------*/

//Config and Profile
enum UserType { REGULAR, NON_REGULAR}

enum Location { WEST, EAST }

//record to prevent global mutation
record AppConfig (
        boolean hasSpecialPermission,
        UserType userType,
        boolean isWeekend,
        boolean isActiveUser,
        boolean isHighPriority,
        Location location
) {
 }
 
 //Right now AppConfigProfile provide a default profile specified in the assignment
 // A customProfile now can be done by instantiate AppConfig
 //Can add other profile types in future
final class AppConfigProfile {
    private AppConfigProfile() {} // prevent instantiation
    static AppConfig defaultProfile() {
        return new AppConfig(
                true,
                UserType.REGULAR,
                false,
                true,
                true,
                Location.WEST
        );
    }
}

/*----------------*/

//Input
record Request(
        String articleName,
        String farmerName,
        String date,
        int quantity
) {}

/*----------------*/

//Centralized FarmSys that takes in all domain related information:
class FarmSystem{
    private final List<Article> articles = new ArrayList<>();
    private final List<Farmer> farmers = new ArrayList<>();
    private final List<Schedule> schedules = new ArrayList<>();
    private final Map<Article, InventoryItem> inventory = new HashMap<>();

    public List<Article> getArticles() {
        return List.copyOf(articles);
    }

    public List<Farmer> getFarmers() {
        return List.copyOf(farmers);
    }

    public List<Schedule> getSchedules() {
        return List.copyOf(schedules);
    }

    public Map<Article, InventoryItem> getInventory() {
        return Map.copyOf(inventory);
    }

    //This addSchedule method allow same date
    public void addSchedule(Article article, String date) {
        schedules.add(new Schedule(article, date));
    }

    public void addStock(Article article, int quantity) {
        InventoryItem item = inventory.computeIfAbsent(article, a -> new InventoryItem(a, 0));
        item.add(quantity);
    }

    public Article addArticle(String name) {
        Article existing = findArticle(name);
        if (existing != null) return existing;

        Article a = new Article(name);
        articles.add(a);
        return a;
    }

    public Farmer addFarmer(String name) {
        Farmer existing = findFarmer(name);
        if (existing != null) return existing;

        Farmer f = new Farmer(name);
        farmers.add(f);
        return f;
    }

    private Article findArticle(String name) {
        for (Article a : articles) {
            if (a.name().equals(name)) return a;
        }
        return null;
    }

    private Farmer findFarmer(String name) {
        for (Farmer f : farmers) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }
}

/*----------------*/

//Process Interface
//Right now the business logic is a trickle down model, that once a process is fulfilled it will stop there
//Made adjustment to business logic to align more to real life
interface Process {
    boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx);
}

final class ProcessContext {
    final Request req;
    boolean highPriority;
    boolean weekend;
    boolean nonRegular;
    boolean nonActive;

    ProcessContext(Request req) {
        this.req = req;
    }

    String articleName() {
        String name = req.articleName();
        if (nonRegular) name += "-NR";
        if (highPriority) name += "-HP";
        if (weekend) name += "-weekend";
        return name;
    }

    String farmerName() {
        String name = req.farmerName();
        if (nonRegular) name += "-NR";
        if(nonActive) name += "-NA";
        if (weekend) name += "-weekend";
        return name;
    }

    String date() {
        String d = req.date();
        if (nonRegular) d += "-NR";
        return d;
    }

    int quantity() { return req.quantity(); }
}

//Process Implementation
final class PermissionProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx){
        return !cfg.hasSpecialPermission();
    }
}

final class NonRegularProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
        if (cfg.userType() != UserType.REGULAR) {
            ctx.nonRegular = true;
        }
        return false;
    }
}

final class HighPriorityProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
        if (cfg.isHighPriority()) ctx.highPriority = true;
        return false;
    }
}

final class WeekendProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
    if(cfg.isWeekend()) {
        ctx.weekend = true;
    }
        return false;
    }
}

//Assignment did not specify what to do if user is inactive
//Add an - "NA" flag to farmer
final class InactiveUserProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
        if(!cfg.isActiveUser()) {
            ctx.nonActive = true;
        }
       return false;
    }
}

final class LocationProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
        Article a = sys.addArticle(ctx.articleName());
        String farmer = ctx.farmerName();

        if (cfg.location() == Location.WEST) {
            sys.addFarmer(farmer);
            sys.addSchedule(a, ctx.date());
            sys.addStock(a, ctx.quantity());
        } else if (cfg.location() == Location.EAST) {
            sys.addFarmer(farmer + "-east");
            sys.addSchedule(a, ctx.date());
            sys.addStock(a, -ctx.quantity());
        }
        return true; // final handler
    }
}

final class ProcessPipeline {
    private final List<Process> processes;

    ProcessPipeline(List<Process> processes) {
        this.processes = processes;
    }

    public void run(AppConfig cfg, FarmSystem sys, ProcessContext ctx) {
        for (Process p : processes) {
            if (p.apply(cfg, sys, ctx)) return;
        }
    }
}

/*-----------------*/

//Driver
final class App {
    private final FarmSystem system;
    private AppConfig config;
    private final ProcessPipeline pipeline;

    App(FarmSystem system, AppConfig config, ProcessPipeline pipeline) {
        this.system = system;
        this.config = config;
        this.pipeline = pipeline;
    }

    //Future possibility to set other types of config
    public void setConfig(AppConfig newConfig) {
        this.config = newConfig;
    }

    public void run(ProcessContext ctx) {
        pipeline.run(config, system, ctx);
    }

    public void displayOutput() {
        System.out.println("Articles:");
        system.getArticles().forEach(System.out::println);

        System.out.println("\nFarmers:");
        system.getFarmers().forEach(System.out::println);

        System.out.println("\nSchedules:");
        system.getSchedules().forEach(s -> System.out.println(s.article().name() + " @ " + s.date()));

        System.out.println("\nInventory:");
        system.getInventory().forEach((a, item) ->
                System.out.println(a.name() + ": " + item.getQuantity())
        );
    }
}

/*------------------*/
public class Main{
    public static void main(String[] args) {
    FarmSystem system = new FarmSystem();
    AppConfig config = AppConfigProfile.defaultProfile();
    ProcessPipeline pipeline = new ProcessPipeline(List.of(
            new PermissionProcess(),
            new NonRegularProcess(),
            new HighPriorityProcess(),
            new WeekendProcess(),
            new InactiveUserProcess(),
            new LocationProcess()
    ));

    App app = new App(system, config, pipeline);
    ProcessContext ctx = new ProcessContext(new Request("Sittake", "John", "2023-10-26", 10));
    app.run(ctx);
    app.displayOutput();
}}



