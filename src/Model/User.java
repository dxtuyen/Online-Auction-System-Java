package Model;

public class User {
    protected String name, dob;

    public User(String name, String dob) {
        this.name = name;
        this.dob = dob;
    }

    public String getName() {
        return name;
    }
}
