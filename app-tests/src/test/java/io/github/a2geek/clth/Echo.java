package io.github.a2geek.clth;

public class Echo {
    public static void main(String[] args) {
        boolean needSpace = false;
        for (String arg : args) {
            if (needSpace) System.out.print(' ');
            needSpace = true;
            System.out.print(arg);
        }
    }
}
