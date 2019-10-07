
/*
 * LibraryModel.java
 * Author:
 * Created on:
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class LibraryModel {

	// For use in creating dialogs and making them modal
	private JFrame dialogParent;
	private Connection con = null;
	// Styling booleans
	private Boolean catalog = false;
	private Boolean indent = true;

	public LibraryModel(JFrame parent, String userid, String password) {
		dialogParent = parent;
		// Register driver
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException cnfe) {
			System.out.println("Can not find" + "the driver class: " + "\nEither I have not installed it"
					+ "properly or \n postgresql.jar " + " file is not in my CLASSPATH");
		}

		// Establish connection
		String url = "jdbc:postgresql:" + "//db.ecs.vuw.ac.nz/" + userid + "_jdbc";
		try {
			this.con = DriverManager.getConnection(url, userid, password);
		} catch (SQLException sqlex) {
			System.out.println("Can not connect: " + sqlex.getMessage());
		}
	}

	/**
	 * Extract details regarding a specific book
	 *
	 * @param isbn
	 *            - book id
	 * @return - string containing corresponding information
	 */
	public String bookLookup(int isbn) {

		StringBuilder output = new StringBuilder();

		// get book
		ResultSet book = execute("SELECT title, edition_no, numofcop, numleft FROM book WHERE isbn = " + isbn);

		// then get author
		ResultSet author = execute("SELECT surname, isbn FROM author NATURAL JOIN book_author WHERE isbn =" + isbn
				+ " ORDER BY authorseqno");

		// header + check if catalog is calling
		if (!catalog)
			output.append("-- Book Lookup --\n\n");

		// construct output
		try {
			if (book.next()) { // if book exists extract data

				output.append((this.indent ? "	" : "") + isbn + ": " + book.getString(1) + "\n	Edition: "
						+ book.getInt(2) + " - #Copies: " + book.getInt(3) + " - #Copies left: " + book.getInt(4)
						+ "\n	Authors: ");

				// no authors
				if (!author.next()) {
					output.append("(No Authors)  ");
				} else {
					// print authors, and check if a name is actually provided
					do {
						output.append((author.getString(1) == null ? "(No author name)"
								: author.getString(1).replace(" ", "")) + ", ");
					} while (author.next());
				}

			} else {
				throw new SQLException();
			}
		} catch (SQLException e) {
			// no book matching isbn
			output.append("Book " + isbn + " does not exsist  ");
		}

		// remove comma on the end with substring
		return output.toString().substring(0, output.toString().length() - 2);
	}

	/**
	 * Returns a catalog of all the books in the library DB
	 *
	 * @return - string representing corresponding information
	 */
	public String showCatalogue() {

		// remove "-- Book lookup --" header
		this.catalog = true;
		// remove indentation from book lookup
		this.indent = false;

		StringBuilder output = new StringBuilder();

		// header
		output.append("-- Show catalogue --\n\n");

		// get all isbns
		ResultSet isbnSet = execute("SELECT isbn FROM book ORDER BY isbn");

		try {
			// get all the book
			while (isbnSet.next()) {
				output.append(bookLookup(isbnSet.getInt(1)) + "\n");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return output.toString();
	}

	/**
	 * Retrieve all customers currently with books on loan and display book data
	 *
	 * @return
	 */
	public String showLoanedBooks() {

		// to remove header from calling booklookup method
		this.catalog = true;

		StringBuilder output = new StringBuilder();

		// header
		output.append("-- Loaned Books --");

		ResultSet loaned = execute("SELECT DISTINCT isbn FROM cust_book");

		// Retrieve book data with isbn, and customer data with customerid
		try {
			while (loaned.next()) {
				// use booklookup method to retrieve formatted data
				output.append("\n\n" + bookLookup(loaned.getInt(1)));

				ResultSet cust_loaned = execute("SELECT customerid FROM cust_book WHERE isbn = " + loaned.getInt(1));
				// iterate through customers who have loaned current book
				while (cust_loaned.next()) {
					ResultSet cust_info = execute(
							"SELECT f_name, l_name, city FROM customer WHERE customerid = " + cust_loaned.getInt(1));
					cust_info.next();
					output.append("\n		" + cust_loaned.getInt(1) + ": " + cust_info.getString(2).replace(" ", "")
							+ ", " + cust_info.getString(1).replace(" ", "") + " - " + cust_info.getString(3));
				}

			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		// reset lookup header
		this.catalog = false;

		return output.toString();
	}

	/**
	 * Show author name, and all books written by given author
	 *
	 * @param authorID
	 * @return
	 */
	public String showAuthor(int authorID) {

		StringBuilder output = new StringBuilder();

		// header
		output.append("-- Show author --\n\n");

		// execute query
		ResultSet rs = execute("SELECT authorid, name, surname, isbn, title " + "FROM (book " + "NATURAL JOIN ("
				+ "(SELECT authorid, name, surname " + "FROM author " + "WHERE authorid=" + authorID + ") AS match "
				+ "NATURAL JOIN " + "book_author) AS merged) AS final");

		try {
			if (rs.next()) {
				output.append(rs.getInt(1) + " - " + rs.getString(2).replace(" ", "") + " " + rs.getString(3) + "\n"
						+ "	Books written: \n		" + rs.getInt(4) + " - " + rs.getString(5));
				while (rs.next()) {
					output.append("\n		" + rs.getInt(4) + " - " + rs.getString(5));
				}
			} else {
				// deal with authors who have no books
				ResultSet noBooks = execute("SELECT authorid, name, surname FROM author WHERE authorid = " + authorID);
				if (noBooks.next()) {
					output.append(noBooks.getInt(1) + " - " + noBooks.getString(2).replace(" ", "") + ", "
							+ noBooks.getString(3) + "\n	(No books)");
				} else {
					output.append("No authors with id: " + authorID);
				}

			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return output.toString();
	}

	/**
	 * Extract all authors names from DB
	 *
	 * @return String containing names
	 */
	public String showAllAuthors() {

		StringBuilder output = new StringBuilder();
		// execute query
		ResultSet rs = execute("SELECT name, surname, authorid  FROM author");
		// context headers
		output.append("-- All Authors --\n\n");
		// extract results
		try {
			while (rs.next()) {
				output.append(
						"	" + rs.getInt(3) + ": " + rs.getString(1).replace(" ", "") + ", " + rs.getString(2) + "\n");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return output.toString();
	}

	/**
	 * Display given customer information and all their books on loan
	 *
	 * @param customerID
	 * @return
	 */
	public String showCustomer(int customerID) {

		String error = "";

		StringBuilder output = new StringBuilder();

		// header
		output.append("-- Show customer --\n\n");

		ResultSet customer = execute("SELECT f_name, l_name, city FROM customer WHERE customerid = " + customerID);

		// extract and format customer information
		try {
			if (customer.next()) {
				output.append("	" + customerID + ": " + customer.getString(2).replace(" ", "") + ", "
						+ customer.getString(1).replace(" ", "") + " - "
						+ (customer.getString(3) == null ? "(No city)" : customer.getString(3)) + "\n");
			} else {
				error = "No record of customer: " + customerID;
				throw new SQLException();
			}

			// get customers books
			output.append("	Books borrowed:\n");

			ResultSet isbnSet = execute("SELECT isbn FROM cust_book WHERE customerid = " + customerID);

			if (isbnSet.next()) {

				do {
					// get books this customer has issued currently
					ResultSet book_data = execute("SELECT title FROM book WHERE isbn = " + isbnSet.getInt(1));
					while (book_data.next()) {
						output.append("		" + isbnSet.getInt(1) + ": " + book_data.getString(1) + "\n");
					}

				} while (isbnSet.next());
			} else {
				output.append("		(No books borrowed)");
			}
		} catch (SQLException e) {
			return error;
		}

		return output.toString();
	}

	/**
	 * Extract and display data from the 'customer' relation for all customers in DB
	 *
	 * @return - String to display data
	 */
	public String showAllCustomers() {

		StringBuilder output = new StringBuilder();

		// execute query
		ResultSet rs = execute("SELECT * FROM customer");

		// header
		output.append("-- All customers --\n\n");
		try {
			while (rs.next()) {
				output.append("	" + rs.getInt(1) + ": " + rs.getString(2).replace(" ", "") + ", "
						+ rs.getString(3).replace(" ", "") + " - "
						+ (rs.getString(4) == null ? "(No city)" : rs.getString(4)) + "\n");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return output.toString();
	}

	/**
	 * Add a tuple into cust_book if a book is available to be borrowed roll back if
	 * error, and lock tuples to ensure data integrity during concurrency
	 *
	 * @param isbn
	 * @param customerID
	 * @param day
	 * @param month
	 * @param year
	 * @return
	 */
	public String borrowBook(int isbn, int customerID, int day, int month, int year) {

		// placeholders for output
		String error = "";
		String fname = "";
		String lname = "";
		String title = "";
		String date = day + " " + getMonth(month) + " " + year;
		int numLeft = -1;

		try {
			// set auto commit to false to suspend the transaction
			this.con.setAutoCommit(false);

			// execute some SQL
			java.sql.Statement s = this.con.createStatement();

			// check for customer and lock if they exist
			ResultSet customer = s
					.executeQuery("SELECT f_name, l_name FROM customer WHERE customerid=" + customerID + " FOR UPDATE");

			// if customer exists store info else abort
			if (customer.next()) {
				fname = customer.getString(1).replace(" ", "");
				lname = customer.getString(2).replace(" ", "");
			} else {
				error = "Customer " + customerID + " does not exist";
				throw new SQLException();
			}

			// check if customer already has book on loan
			ResultSet loanCheck = s.executeQuery(
					"SELECT isbn, customerid FROM cust_book WHERE isbn = " + isbn + " AND customerid = " + customerID);
			if (loanCheck.next()) {
				// customer already has book on loan
				error = "Customer: " + customerID + " already has book: " + isbn + " on loan";
				throw new SQLException();
			}

			// check the book is available and lock it
			ResultSet bookTitle = s
					.executeQuery("SELECT title, numleft FROM book WHERE isbn = " + isbn + " FOR UPDATE");

			// check there was enough
			if (bookTitle.next()) {
				// get title
				title = bookTitle.getString(1).replaceAll("[ \t]+$", "");
				numLeft = bookTitle.getInt(2);
				// check num left
				if (numLeft <= 0) {
					error = "There is no available copies of book: " + isbn;
					throw new SQLException();
				}
			} else {
				error = "Book: " + isbn + " does not exist";
				throw new SQLException();
			}

			// alert user and attempt concurrent transaction
			JOptionPane.showMessageDialog(this.dialogParent, "Tuples locked ready for update, press ok to continue",
					"Program suspended", JOptionPane.PLAIN_MESSAGE);

			// insert into cust_book table
			s.executeUpdate("INSERT INTO cust_book VALUES(" + isbn + ",'" + year + "-" + month + "-" + day + "',"
					+ customerID + ");");

			// update num left
			s.executeUpdate("UPDATE book SET numleft = " + (numLeft - 1) + " WHERE isbn = " + isbn);

			// commit
			this.con.commit();
			// set back to true as best practice
			this.con.setAutoCommit(true);

		} catch (SQLException e) {
			try {
				this.con.rollback();
				this.con.setAutoCommit(true);
				return error;
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}

		return "Borrow Book:\n Book: " + isbn + " (" + title + ")\n Loaned to: " + customerID + " (" + fname + " "
				+ lname + ")\n Due Date: " + date;
	}

	/**
	 * Return a book if a user has that book out on loan
	 *
	 * @param isbn
	 * @param customerid
	 * @return
	 */
	public String returnBook(int isbn, int customerid) {

		String error = "";

		try {

			// set auto commit to false
			this.con.setAutoCommit(false);

			java.sql.Statement s = this.con.createStatement();

			// check whether the customer has that booked loaned out and lock
			ResultSet customer = s.executeQuery(
					"SELECT * FROM cust_book WHERE isbn = " + isbn + " AND customerid = " + customerid + " FOR UPDATE");

			// if the customer does not have that book issued roll back
			if (!customer.next()) {
				error = "Customer: " + customerid + " currently is not borrowing book: " + isbn;
				throw new SQLException();
			} else {
				// return book by removing tuple
				s.executeUpdate("DELETE FROM cust_book WHERE isbn = " + isbn + " AND customerid = " + customerid + ";");
			}

			// update count in book table
			// get current count
			ResultSet book = s.executeQuery("SELECT numleft FROM book WHERE isbn = " + isbn);
			book.next();

			// update count
			s.executeUpdate("UPDATE book SET numleft = " + (book.getInt(1) + 1) + " WHERE isbn = " + isbn);

			// commit
			this.con.commit();
			this.con.setAutoCommit(true);

		} catch (SQLException e) {

			// rollback if error
			try {
				this.con.rollback();
				this.con.setAutoCommit(true);
				return error;
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}

		return "Return book: \n Book: " + isbn + " has been returned by customer: " + customerid;
	}

	/**
	 * Disconnect database
	 */
	public void closeDBConnection() {
		try {
			this.con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Delete a customer from the customer table, only allow if they have a book out
	 *
	 * @param customerID
	 * @return
	 */
	public String deleteCus(int customerID) {

		String error = "";

		try {

			this.con.setAutoCommit(false);
			java.sql.Statement s = this.con.createStatement();

			ResultSet customer = s
					.executeQuery("SELECT * FROM customer WHERE customerid = " + customerID + " FOR UPDATE");

			// check customer exists
			if (customer.next()) {
				// check if the customer has a book loaned, if so restrict deletion
				ResultSet books = s
						.executeQuery("SELECT * FROM cust_book WHERE customerid = " + customerID + " FOR UPDATE");
				if (!books.next()) {
					System.out.print("DELETE");
					// delete customer
					s.executeUpdate("DELETE FROM customer WHERE customerid = " + customerID + ";");
				} else {
					error = "Cannot delete customer: " + customerID + " as customer has a book on loan";
					return error;
				}
			} else {
				error = "No such customer: " + customerID;
				return error;
			}

			// commit
			this.con.commit();
			this.con.setAutoCommit(true);

		} catch (SQLException e) {

			try {
				this.con.rollback();
				this.con.setAutoCommit(true);
				return error;
			} catch (SQLException e1) {

				e1.printStackTrace();
			}
			e.printStackTrace();
		}

		return "Customer: " + customerID + " was successfully deleted";
	}

	/**
	 * Delete an author from an author table and cascade the deletion
	 *
	 * @param authorID
	 * @return
	 */
	public String deleteAuthor(int authorID) {

		String error = "";

		try {

			java.sql.Statement s = this.con.createStatement();

			this.con.setAutoCommit(false);

			// check author is in author table and lock
			ResultSet author = s.executeQuery("SELECT * FROM author WHERE authorid = " + authorID + " FOR UPDATE");

			// if no author roll back
			if (author.next()) {

				// delete author from author and book_author
				s.executeUpdate("DELETE FROM author WHERE authorid = " + authorID + ";");
			} else {
				error = "No such author: " + authorID;
				throw new SQLException();
			}

			// commit changes
			this.con.commit();
			this.con.setAutoCommit(true);

		} catch (SQLException e) {
			e.printStackTrace();
			try {
				this.con.setAutoCommit(true);
				this.con.rollback();
				return error;
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}

		return "Sucessfully deleted author: " + authorID;
	}

	public String deleteBook(int isbn) {
		return "Delete Book";
	}

	/**
	 * Method used to execute SQL statements
	 */
	public ResultSet execute(String query) {

		ResultSet rs = null;

		try {
			// attempt to execute SQL query
			java.sql.Statement s = this.con.createStatement();
			rs = s.executeQuery(query);
		} catch (SQLException e) {
			System.out.println("Error executing: " + query + ", " + e.getMessage());
		}

		if (rs == null) {
			System.out.println("RS is null");
		}
		// return results
		return rs;
	}

	/**
	 * Returns the appropriate string given a month value
	 *
	 */
	public String getMonth(int month) {

		switch (month) {
		case 1:
			return "Jan";
		case 2:
			return "Feb";
		case 3:
			return "Mar";
		case 4:
			return "Apr";
		case 5:
			return "May";
		case 6:
			return "Jun";
		case 7:
			return "Jul";
		case 8:
			return "Aug";
		case 9:
			return "Sep";
		case 10:
			return "Oct";
		case 11:
			return "Nov";
		case 12:
			return "Dec";
		}

		return "Not a valid month value";
	}

}