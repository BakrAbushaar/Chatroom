

//bna24
//November 11, 2024
package Project.Common;

public class RollPayload extends Payload {
    private int diceCount;
    private int diceSides;
    

    // Constructor for RollPayload
    public RollPayload() {
        super.setPayloadType(PayloadType.ROLL);

    }

    // Getters and Setters
    public int getDiceCount() {
        return diceCount;
    }

    public void setDiceCount(int diceCount) {
        this.diceCount = diceCount;
    }

    public int getDiceSides() {
        return diceSides;
    }

    public void setDiceSides(int diceSides) {
        this.diceSides = diceSides;
    }
}
