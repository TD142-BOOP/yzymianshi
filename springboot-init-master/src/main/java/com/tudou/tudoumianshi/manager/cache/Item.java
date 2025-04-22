package com.tudou.tudoumianshi.manager.cache;

/**
 * 代替Java 16+ record类型的普通JDK 8兼容类
 */
public class Item {
    private final String key;
    private final int count;

    public Item(String key, int count) {
        this.key = key;
        this.count = count;
    }

    public String key() {
        return key;
    }

    public int count() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return count == item.count && 
               (key == null ? item.key == null : key.equals(item.key));
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + count;
        return result;
    }

    @Override
    public String toString() {
        return "Item{" +
                "key='" + key + '\'' +
                ", count=" + count +
                '}';
    }
}
