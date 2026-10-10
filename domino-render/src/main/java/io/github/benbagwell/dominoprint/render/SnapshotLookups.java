package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.DocumentSnapshot;
import java.util.*;

/** Entirely in-memory lookup implementation. Database identities and aliases are explicit. */
public final class SnapshotLookups implements LookupProvider {
    public record Location(String server,String database) { public Location { server=norm(server);database=norm(database); } }
    private record ViewKey(Location location,String view) { ViewKey { view=norm(view); } }
    private final Map<ViewKey,ViewSnapshot> views;
    private final Map<Location,Location> aliases;
    private final Map<String,DocumentSnapshot> documents;
    private final Map<String,DocumentSnapshot> profiles;
    private final Location current;
    private final CodeTranslations codes;
    public SnapshotLookups(Location current,List<ViewSnapshot> views,Map<Location,Location> aliases,Map<String,DocumentSnapshot> documents,Map<String,DocumentSnapshot> profiles,CodeTranslations codes){
        this.current=Objects.requireNonNull(current);this.aliases=Map.copyOf(aliases);this.codes=Objects.requireNonNull(codes);this.documents=normalizeMap(documents);this.profiles=normalizeMap(profiles);
        Map<ViewKey,ViewSnapshot> out=new LinkedHashMap<>();for(var view:views){List<String> names=new ArrayList<>(view.aliases());names.add(view.name());for(String name:names){var key=new ViewKey(resolve(new Location(view.server(),view.database())),name);if(out.putIfAbsent(key,view)!=null)throw new IllegalArgumentException("Duplicate view: "+name);}}this.views=Map.copyOf(out);
    }
    public static SnapshotLookups empty(CodeTranslations codes){return new SnapshotLookups(new Location("",""),List.of(),Map.of(),Map.of(),Map.of(),codes);}
    private SnapshotLookups(SnapshotLookups source,CodeTranslations codes){current=source.current;views=source.views;aliases=source.aliases;documents=source.documents;profiles=source.profiles;this.codes=Objects.requireNonNull(codes);}
    public SnapshotLookups withCodes(CodeTranslations codes){return new SnapshotLookups(this,codes);}
    public static String documentKey(String database,String unid){return norm(database)+"\u0000"+norm(unid);}
    public static String profileKey(String database,String profile,String key){return norm(database)+"\u0000"+norm(profile)+"\u0000"+key;}
    private static String norm(String s){return Objects.requireNonNull(s).replace('\\','/').toLowerCase(Locale.ROOT);}
    private static Map<String,DocumentSnapshot> normalizeMap(Map<String,DocumentSnapshot> map){Map<String,DocumentSnapshot> out=new HashMap<>();map.forEach((k,v)->{if(out.put(norm(k),v)!=null)throw new IllegalArgumentException("Duplicate document resource: "+k);});return Map.copyOf(out);}
    private Location resolve(Location location){Set<Location> seen=new HashSet<>();while(aliases.containsKey(location)){if(!seen.add(location))throw new IllegalArgumentException("Database alias cycle");location=aliases.get(location);}return location;}
    private ViewSnapshot view(String server,String database,String view){var location=resolve(database.isEmpty()?current:new Location(server,database));return views.get(new ViewKey(location,view));}
    @Override public FormulaValue lookup(Request r){var view=view(r.server(),r.database(),r.view());return view==null?FormulaValue.error("MISSING_RESOURCE","View not cached: "+r.view()):view.lookup(r);}
    @Override public FormulaValue column(String server,String database,String name,int column){var view=view(server,database,name);return view==null?FormulaValue.error("MISSING_RESOURCE","View not cached: "+name):view.column(column);}
    @Override public FormulaValue documentField(String database,String unid,String field){var doc=documents.get(documentKey(database.isEmpty()?current.database():database,unid));return field(doc,field);}
    @Override public FormulaValue profileField(String database,String profile,String key,String field){return field(profiles.get(norm(profileKey(database.isEmpty()?current.database():database,profile,key))),field);}
    private static FormulaValue field(DocumentSnapshot doc,String field){return doc==null?FormulaValue.error("MISSING_RESOURCE","Document/profile not cached"):doc.item(field).map(FormulaValue::from).orElse(FormulaValue.unavailable());}
    @Override public FormulaValue translate(String set,String code){return codes.translate(set,code);}
}
