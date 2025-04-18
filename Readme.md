# Project Setup Instructions

This guide provides step-by-step instructions to set up and run the project, including configuring credentials, Docker, and the Apex application.

## Prerequisites
- Docker installed and running on your system.
- Access to the Apex application.
- An ngrok account with an authentication token.

## Setup Steps

1. **Configure Credentials**
    - Add the `credentials.json` file to the `src/main/resources/` directory.

2. **Set Up Application Properties**
    - Locate the `src/main/resources/application-example.properties` file.
    - Create a copy of this file and name it `application.properties`.
    - Open `application.properties` and fill in the `apex.api.key` with your API key.

3. **Configure Docker Compose**
    - Copy the `docker-compose-example.yml` file and rename the copy to `docker-compose.yml`.
    - Open `docker-compose.yml` and insert your `NGROK_AUTHTOKEN`.

4. **Run the Application**
    - Start the application script. A link will appear in the console.
    - Click the link, select your email, and grant access as prompted.

5. **Run Docker Container**
    - Ensure Docker is running.
    - Open a terminal and navigate to the project directory.
    - Run the following command to start the ngrok container:
      ```bash
      docker compose up -d
      ```
      
    - To stop container run 
      ```bash
      docker compose down
      ```

6. **Update Apex with Public URL**

    - While the Docker container is running, visit http://localhost:4040/api/tunnels.
    - Copy the PublicURL value from the ngrok dashboard.
    - In the Apex application, navigate to Application > Page 4: Ticket Reply Page.
    - On the left tab, select Reply Process.
    - Update the PL/SQL Code by setting the l_url variable to the copied PublicURL.


## Project Folder Structure
Below is the expected project folder structure after completing the setup:

![Screenshot 2025-04-18 at 14.42.50.png](files/Screenshot%202025-04-18%20at%2014.42.50.png)

Ngrok Tunnel Dashboard
The ngrok tunnel dashboard should look like this when the container is running:

![Screenshot 2025-04-18 at 14.43.28.png](files/Screenshot%202025-04-18%20at%2014.43.28.png)

Apex Reply Process
The Apex Reply Process configuration in the application should resemble the following:

![Screenshot 2025-04-18 at 14.45.33.png](files/Screenshot%202025-04-18%20at%2014.45.33.png)