package Model;

public class Bidder extends User {
    public Bidder(String name, String dob) {
        super(name, dob);
    }

    public void setName(String newName) {
        name = newName;
    }
}
