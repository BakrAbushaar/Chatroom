

//bna24
//November 11, 2024
package Project.Common;

public class RollPayload extends Payload {
    private int diceCount;
    private int diceSides;
    

    // Constructor for RollPayload
    public RollPayload(int diceCount, int diceSides) {
        super.setPayloadType(PayloadType.ROLL);
        this.diceCount = diceCount;
        this.diceSides = diceSides;

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
