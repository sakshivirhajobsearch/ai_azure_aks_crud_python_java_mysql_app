import mysql.connector

db = mysql.connector.connect(
    host="localhost",
    user="root",
    password="admin",
    database="ai_azure_aks"
)

db_cursor = db.cursor(dictionary=True)

def close_db():
    db_cursor.close()
    db.close()
