package ru.mirea.practice8.delivery;
import java.util.Map;
public final class DeliveryTransitions {
    private DeliveryTransitions(){}
    private static final Map<String,String> NEXT=Map.of(
        "CREATED","ACCEPTED","ACCEPTED","IN_TRANSIT","IN_TRANSIT","DELIVERED");
    public static boolean allowed(String current,String next){
        return next!=null && next.equals(NEXT.get(current));
    }
}
