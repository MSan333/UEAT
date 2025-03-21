package com.hmdp.agli;

import java.util.*;


  class ListNode {
      int val;
      ListNode next;
     ListNode() {}
     ListNode(int val) { this.val = val; }
      ListNode(int val, ListNode next) { this.val = val; this.next = next; }
  }

public class AgliV1 {
    public static ListNode reverseList(ListNode head) {

        ListNode Phead = new ListNode();

        ListNode cur =  head;
        while(cur != null){
            ListNode temp = cur.next;
            cur.next = Phead.next;
            Phead.next = cur;
            cur = temp;
        }
        return Phead.next;

    }
    public static boolean isPalindrome(ListNode head) {
        int length = 0;
        ListNode cur = head;
        while(cur != null){
            length++;
            cur = cur.next;
        }
        int half = length / 2;
        cur = head;
        while(half != 0){
            cur = cur.next;
            half--;
        }
        ListNode tail = reverseList(cur);
        half = length / 2;
        while(half != 0){
            if(tail.val == head.val){
                tail = tail.next;
                head = head.next;
                half--;
            }else{
                return false;
            }
        }
        return true;
    }
    public static void setZeroes(int[][] matrix) {
        int[] row = new int[]{0,1,0,-1};
        int[] col = new int[]{1,0,-1,0} ;

        int[][] used = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            used[i] = Arrays.copyOf(matrix[i], matrix[i].length);
        }


        for(int i = 0; i < matrix.length; i++){
            for(int j = 0; j < matrix[0].length; j++){
                if(matrix[i][j] == 0 && used[i][j] == 0){
                    if(i > 0){
                        matrix[i - 1][j] = 0;
                    }
                    if(i < matrix.length - 1){
                        matrix[i + 1][j] = 0;
                    }
                    if(j > 0 ){
                        matrix[i][j - 1] = 0;
                    }
                    if(j < matrix[0].length - 1){
                        matrix[i][j + 1] = 0;
                    }
                }
            }
        }
        System.out.println(Arrays.deepToString(matrix));
      Map<Integer, Integer> map = new HashMap<>();
      map.put(1, 2);
      map.put(2, 3);
      map.put(3, 4);
      Deque<Integer> deque = new LinkedList<>();
      deque.add(1);
      deque.add(2);
      deque.add(3);

    }

    public static int lengthOfLongestSubstring(String s) {

        if(s.length() < 2)
            return s.length();
        String ans22 = "1111";
        List<Integer> integers = new ArrayList<>();
        integers.add(3);
        integers.remove(3);





        Set<String> charSet = new HashSet<>();
        int left = 0;
        int cur = 1, ans = 1;
        Map<Character, Integer> map = new HashMap<>();
        charSet.add(s.charAt(0) + "");
        for(int i = 1; i < s.length(); i++){
            if(charSet.contains(s.charAt(i) + "")){
                while(left < i){
                    if(s.charAt(left) != s.charAt(i)){
                        cur--;
                        charSet.remove(s.charAt(left++) + "");
                    }else{
                        left++;
                        break;
                    }
                }

            }
            else{
                cur++;
                charSet.add(s.charAt(i) + "");
            }
            ans = Math.max(ans, cur);

        }
        return ans;
    }
    static boolean dfs(char[][] board, boolean[][] used, String word, int i, int j, int index){
        if(index == word.length())
            return true;


        if(board[i][j] == word.charAt(index) && used[i][j] == false){
            used[i][j] = true;
            boolean f1 = false, f2 = false, f3 = false, f4 = false;
            if(i + 1 < board.length)
                f1 = dfs(board, used,word, i + 1, j , index + 1);
            if(i - 1 >= 0)
                f2 = dfs(board, used,word, i - 1, j , index + 1);
            if(j + 1 < board[0].length)
                f3 = dfs(board, used,word, i, j + 1 , index + 1);
            if(j - 1 >= 0)
                f4 = dfs(board, used,word, i , j - 1 , index + 1);
            used[i][j] = false;
            return f1 || f2 || f3 || f4;

        }
        return false;
    }
    public static boolean exist(char[][] board, String word) {
        boolean[][] used = new boolean[board.length][board[0].length];
        for(int i = 0; i < board.length; i++){
            for(int j = 0; j < board[0].length; j++){
                if(dfs(board, used, word,i, j, 0))
                    return true;
            }
        }
        return false;
    }

    public static List<List<Integer>> generate(int numRows) {
        List<List<Integer>> ans = new ArrayList<>();
        for(int i = 0; i < numRows; i++){
            List<Integer> temp = new ArrayList<>();
            if(i < 2){
                for(int j = 0; j < 2; j++){
                    temp.add(1);
                }

            }else{
                temp.add(1);
                for(int j = 1; j < i - 1; j++){
                    temp.add(ans.get(i - 1).get(j - 1) + ans.get(i - 1).get(j));
                }
                temp.add(1);
            }
            ans.add(temp);
        }
        return ans;
    }

    public static void main(String[] args) {
//        lengthOfLongestSubstring("pwwkew");
//        setZeroes(new int[][]{{1, 1, 1},{1,0,1},{1,1,1}});
//        Integer.parseInt()
       generate(5);
       String s = "123";
       Math.abs(1);

       StringBuilder sb = new StringBuilder();
        boolean abcced = exist(new char[][]{{'A','B','C','E'},{'S','F','C','S'},{'A','D','E','E'}}, "ABCCED");
//        boolean palindrome = isPalindrome(new ListNode(1, new ListNode(2, null)));
        System.out.println(abcced);
    }








}
