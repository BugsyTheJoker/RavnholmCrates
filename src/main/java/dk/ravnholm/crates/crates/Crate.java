package dk.ravnholm.crates.crates;

public class Crate {

    private final String id;
    private final String name;

    public Crate(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
