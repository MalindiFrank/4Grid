package za.co.fourgrid.db.memory;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import za.co.fourgrid.model.Places;
import za.co.fourgrid.model.Town;

/**
 * /*
 * In-memory implementation of the Places interface.
 *
 * This implementation is suitable for testing or for applications where the
 * dataset is small and can be entirely loaded into memory.
 */
public class PlacesDb implements Places
{
    private final Set<Town> towns = new TreeSet<>();

    public PlacesDb( Set<Town> places ){
        towns.addAll( places );
    }

    @Override
    public Collection<String> provinces(){
        return towns.parallelStream()
            .map( town -> town.getProvince() )
            .collect( Collectors.toSet() );
    }

    @Override
    public Collection<Town> townsIn( String aProvince ){
        return towns.parallelStream()
            .filter( aTown -> aTown.getProvince().equals( aProvince ))
            .collect( Collectors.toSet() );
    }

    @Override
    public int size(){
        return towns.size();
    }

}