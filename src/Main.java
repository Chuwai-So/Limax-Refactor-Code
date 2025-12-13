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
interface Process {
    boolean apply(AppConfig cfg, FarmSystem sys, Request req);
}

//Process Implementation
final class PermissionProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {
        return !cfg.hasSpecialPermission();
    }
}

final class NonRegularProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {
        if (cfg.userType() == UserType.REGULAR) return false;

        Article a = sys.addArticle(req.articleName() + "-NR");
        sys.addFarmer(req.farmerName() + "-NR");
        sys.addSchedule(a, req.date() + "-NR");

        return true;
    }
}

final class HighPriorityProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {
        if (!cfg.isHighPriority()) return false;

        sys.addArticle(req.articleName() + "-HP");
        return true;
    }
}

final class WeekendProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {
        if (!cfg.isWeekend()) return false;

        sys.addArticle(req.articleName() + "-weekend");
        sys.addFarmer(req.farmerName() + "-weekend");

        return true; // STOP
    }
}

//Assignment did not specify what to do if user is inactive
final class InactiveUserProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {
        // stop if inactive, do nothing
        return !cfg.isActiveUser();
    }
}

final class LocationProcess implements Process {
    public boolean apply(AppConfig cfg, FarmSystem sys, Request req) {

        Article a = sys.addArticle(req.articleName());

        //Did not specify to add "west" flag if location is set to west
        if (cfg.location() == Location.WEST) {
            sys.addFarmer(req.farmerName());
            sys.addSchedule(a, req.date());
            sys.addStock(a, req.quantity());

        } else if (cfg.location() == Location.EAST) {
            sys.addFarmer(req.farmerName() + "-east");
            sys.addSchedule(a, req.date());
            sys.addStock(a, -req.quantity());
        }

        return true; //Always stop as final handler
    }
}

final class ProcessPipeline {
    private final List<Process> processes;

    ProcessPipeline(List<Process> processes) {
        this.processes = processes;
    }

    public void run(AppConfig cfg, FarmSystem sys, Request req) {
        for (Process p : processes) {
            if (p.apply(cfg, sys, req)) {
                return;
            }
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

    public void setConfig(AppConfig newConfig) {
        this.config = newConfig;
    }

    public void run(Request req) {
        pipeline.run(config, system, req);
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

    app.run(new Request("Shiitake", "John", "2023-10-26", 10));
    app.displayOutput();
}}



