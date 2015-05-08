package hk.ust.comp4641.project.main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hk.ust.comp4641.project.dataParser.*;
import hk.ust.comp4641.project.dataType.*;
import jdbm.helper.FastIterator;
import jdbm.htree.HTree;
import Jama.*; 

public class Main {
	// Item-based Collaborative Filtering
	private static HTree customerList = DataParser.getCustomerList();
	private static HTree itemList = DataParser.getItemList();

	public static void main(String[] args) throws IOException{
		// Initialization
		FastIterator citer, iiter;
		String ckey, ikey;
		int i_size, c_size;
		HashMap<String, List<String>> itemDict = new HashMap<String, List<String>>();
		HashMap<String, List<Review>> cusDict = new HashMap<String, List<Review>>();
		List<String> itemProfile, userProfile;
		
		// Similar matrix table and user rating will be stored here.
		Matrix similarMatrix, userMatrix;
		// prediction matrix: prediction[user A][item X].
		// value = -1 if the customer has declared.
		Matrix predictionMatrix;

		
		iiter = itemList.keys();
		citer = customerList.keys();
		while((ikey = (String)iiter.next()) != null){
			itemDict.put(ikey, ((Item) itemList.get(ikey)).getSimilarList());
//			System.out.println(ikey + " " + itemDict.get(ikey));
		}
		while((ckey = (String)citer.next()) != null){
			cusDict.put(ckey, ((Customer) customerList.get(ckey)).getReviewList());
			System.out.println(ckey + " " + cusDict.get(ckey));
		}

		// re-initialize the iterator.
		iiter = itemList.keys();
		citer = customerList.keys();

		// Ensure for item A that is similar to item B, item B is on the map and is similar to item A.
		while((ikey = (String)iiter.next()) != null){
			List<String> tempList = itemDict.get(ikey);

			if(tempList == null){
				continue;
			}
			for(int i = 0; i < tempList.size(); i++){
				// If the hashMap has item B, then check if item B is similar to item A. 
				String itemB = tempList.get(i);

				if(itemDict.containsKey(itemB) && !ikey.equals(itemB)){
					if(! tempList.contains(ikey)){
						// update that item B is similar to item A

						itemDict.get(itemB).add(ikey);
					}
				}else{
					// add item B to the hashMap
					List<String> tempList2 = new ArrayList<String>();
					tempList2.add(ikey);
					itemDict.put(itemB, tempList2);
				}
			}
		}


		/** Check: print the whole item hashMap. **/
		/**
		System.out.println("Item hashMap.");
		for(String str : itemDict.keySet()){
			System.out.print(str + "\t");
			if(itemDict.get(str) == null){
				System.out.println("NO similar!");
				continue;
			}

			System.out.println(itemDict.get(str));
		}
		System.out.println("Customer hashMap.");
		for(String str : cusDict.keySet()){
			System.out.print(str + "\t");
			if(cusDict.get(str) == null){
				System.out.println("No rating. (?)");
				continue;
			}
			for(int i = 0; i < cusDict.get(str).size(); i++)
			System.out.println(cusDict.get(str).get(i).getASIN() + "///" + cusDict.get(str).get(i).getRating());
		}
		/**  END CHECK  **/

		
		// Create a matrix
		i_size = itemDict.size();
		c_size = cusDict.size();
		itemProfile = new ArrayList<String>();
		userProfile = new ArrayList<String>();
		double[][] simTable = new double[i_size][i_size];
		double[][] ratingTable = new double[c_size][i_size];

		//		for (int k = 0; k < simTable.length; k++){
		//			simTable[k][k] = -1.0;
		//		}

		for(String str : itemDict.keySet()){
			itemProfile.add(str);
		}
		for(String str : cusDict.keySet()){
			userProfile.add(str);
		}
		
		// If item A & B is similar, put 1 to the corresponding entry. Otherwise, it's default 0. 
		{
			int index = 0;
			for(String str : itemDict.keySet()){
				if(itemDict.get(str) != null){
					for(String simList : itemDict.get(str)){
						int simIndex = itemProfile.indexOf(simList);
						simTable[index][simIndex] = 1.0;
					}
				}
				index++;
			}
		}
		
		// raingTable 2D array: customer ID = row, column represent product with rating. 
		// Order of product based on index of that in itemProfile.
		{
			int index = 0;
			for(String str : cusDict.keySet()){
				if(cusDict.get(str) != null){
					for(Review rev : cusDict.get(str)){
						int columnOfProduct = itemProfile.indexOf(rev.getASIN());
						if(columnOfProduct == -1){
							System.err.println("ERROR: no such ASIN in product list");
						}else{
							ratingTable[index][columnOfProduct] = rev.getRating();
						}
					}
				}
				index++;
			}
		}



		/** Checking the double array. **/
		/** item **/
		/**
		for(int i = 0; i < simTable.length; i++){
			for(int j = 0; j < simTable[i].length; j++){
				System.out.print(simTable[i][j] + "\t");
			}
			System.out.println();
		}
		/** rating **/
		/**
		System.out.println("Rating");
		for(int i = 0; i < ratingTable.length; i++){
			for(int j = 0; j < ratingTable[i].length; j++){
				System.out.print(ratingTable[i][j] + "\t");
			}
			System.out.println();
		}
		/**  END CHECK  **/

		similarMatrix = new Matrix(simTable);
		userMatrix = new Matrix(ratingTable);

		// Create matrix with [1, 1, ..., 1]
		Matrix colVector = new Matrix(i_size, 1, 1.0);
		Matrix numVector = new Matrix(i_size, 1);
		numVector = similarMatrix.times(colVector);
		
		/** Check **/
		/**
		System.out.println("numVector:");
		numVector.print(i_size, 1);
		/**  END CHECK  **/

		// divide the number by sqrt of row sum.
		for(int i = 0; i < i_size; i++){
			for(int j = 0; j < i_size; j++){
				if(numVector.get(i, 0) == 0){
					continue;
				}
				double temp = similarMatrix.get(i, j);
				similarMatrix.set(i, j, temp / Math.sqrt(numVector.get(i, 0)));
			}
		}
		
		predictionMatrix = userMatrix.times(similarMatrix);
		// set value to -1 if the corresponding entry has the exact rating.
		for(int i = 0; i < predictionMatrix.getRowDimension(); i++){
			for(int j = 0; j < predictionMatrix.getColumnDimension(); j++){
				if(ratingTable[i][j] != 0.0){
					predictionMatrix.set(i, j, -1);
				}
			}
		}

		/** Check **/
		/**
		System.out.println("Adjusted matrix.");
		similarMatrix.print(i_size, i_size);
		System.out.println("Prediction.");
		predictionMatrix.print(i_size, c_size);
		/** END CHECK **/
		

		// Terminate the data parser before exit
		DataParser.terminate();

	}

}
