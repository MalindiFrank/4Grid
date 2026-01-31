package za.co.fourgrid.common.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * I am a data/transfer object for communicating the loadshedding stage.
 */
public class StageDO
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private int stage;

    /**
     * Default constructor is needed otherwise the JSON mapper
     * can't create an instance.
     */
    public StageDO(){
        stage = 0;
    }

    public StageDO( int s ){
        stage = s;
    }

    public int getStage(){
        return stage;
    }

    public String asJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "StageDO{" +
                "stage=" + stage +
                '}';
    }
}
