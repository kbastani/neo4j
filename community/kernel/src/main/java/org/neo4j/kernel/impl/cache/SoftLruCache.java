/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.cache;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SoftLruCache<E extends EntityWithSizeObject> extends ReferenceCache<E>
{
    private final ConcurrentHashMap<Long,SoftValue<Long,E>> cache = new ConcurrentHashMap<>();
    private final SoftReferenceQueue<Long,E> refQueue = new SoftReferenceQueue<>();
    private final String name;
    private final HitCounter counter = new HitCounter();

    public SoftLruCache( String name )
    {
        this.name = name;
    }

    @Override
    public E put( E value )
    {
        Long key = value.getId();
        SoftValue<Long,E> ref = new SoftValue<>( key, value, (ReferenceQueue) refQueue );
        SoftValue<Long, E> previous = cache.putIfAbsent( key, ref );
        pollClearedValues();
        return previous != null ? previous.get() : value;
    }

    @Override
    public void putAll( Collection<E> list )
    {
        Map<Long,SoftValue<Long,E>> softMap = new HashMap<>( list.size() * 2 );
        for ( E entry : list )
        {
            Long key = entry.getId();
            SoftValue<Long,E> ref =
                new SoftValue<>( key, entry, (ReferenceQueue) refQueue );
            softMap.put( key, ref );
        }
        cache.putAll( softMap );
        pollClearedValues();
    }

    @Override
    public E get( long key )
    {
        SoftReference<E> ref = cache.get( key );
        if ( ref != null )
        {
            if ( ref.get() == null )
            {
                cache.remove( key );
            }
            return counter.count( ref.get() );
        }
        return counter.<E>count( null );
    }

    @Override
    public E remove( long key )
    {
        SoftReference<E> ref = cache.remove( key );
        if ( ref != null )
        {
            return ref.get();
        }
        return null;
    }

    @Override
    protected void pollClearedValues()
    {
        SoftValue<Long,E> clearedValue = refQueue.safePoll();
        while ( clearedValue != null )
        {
            cache.remove( clearedValue.key );
            clearedValue = refQueue.safePoll();
        }
    }

    @Override
    public long size()
    {
        return cache.size();
    }

    @Override
    public void clear()
    {
        cache.clear();
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public long hitCount()
    {
        return counter.getHitsCount();
    }

    @Override
    public long missCount()
    {
        return counter.getMissCount();
    }
}
