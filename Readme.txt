1. Compile the code: javac Server.java
2. Run the code: java Server
3. Add number of neighbors
4. Add their address and link cost.

The program will ask you for number of neighbors. I am considering one link between two nodes hence don't add same neighbor twice.
e.g.

If router A is connected to B and C
you can do that by saying A is connected to 1 neighbor and add B then for C you can add A as neighbor or add both B and C as neighbor but in that case don't add A as C's neighbor again.
