package za.co.fourgrid.common.util;

/**
 * Interface for empty checks
 */
public interface Checks
{
    static String checkNotEmpty( String aString ){
        if( aString == null || aString.isEmpty() ){
            throw new IllegalArgumentException();
        }
        return aString;
    }
}
