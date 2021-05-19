package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class NioTelnetServer {
	public static final String LS_COMMAND = "ls    view all files and directories\r\n";
	public static final String MKDIR_COMMAND = "mkdir [directory]   create directory\r\n";
	public static final String CHANGE_NICKNAME = "nick [name]   change nickname\r\n";
	public static final String TOUCH_COMMAND = "touch [filename]	create file\r\n";
	public static final String CD_PATH_COMMAND =
			"cd [path]    change directory\r\ncd .. 	change directory to up\r\ncd ~	 change directory to root\r\n";
	public static final String REMOVE_COMMAND = "rm [filename]/[directory name]    remove file/directory\r\n";
	public static final String COPY_COMMAND = "copy [filename] [path]    copy file to directory path\r\n";
	public static final String CAT_COMMAND = "cat [filename]		open file\r\n";

	private String nickname = "User";
	private final ByteBuffer buffer = ByteBuffer.allocate(512);
	private int numberOfNewFolders = 0;
	private int numberOfNewFiles = 0;
	private String currentDirectory = "server/";


	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			Set<SelectionKey> selectionKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {

			return;
		}

		buffer.flip();

		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}

		buffer.clear();

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");

			String[] commands = command.split(" ");

			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(TOUCH_COMMAND, selector, client);
				sendMessage(CD_PATH_COMMAND, selector, client);
				sendMessage(REMOVE_COMMAND, selector, client);
				sendMessage(COPY_COMMAND, selector, client);
				sendMessage(CAT_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME, selector, client);
			} else if ("ls".equals(command)) {
				sendMessage(getFileList().concat("\r\n"), selector, client);
			} else if ("nick".equals(commands[0])) {
				changeNickname(commands, channel);
			} else if ("touch".equals(commands[0])) {
				createFile(commands);
			} else if ("mkdir".equals(commands[0])) {
				createDirectory(commands);
			} else if ("cat".equals(commands[0])) {
				showFile(commands, channel);
			} else if ("rm".equals(commands[0])) {
				deleteFile(commands, channel);
			} else if ("copy".equals(commands[0])) {
				copyFile(commands, channel);
			} else if ("cd".equals(commands[0])) {
				changeDirectory(commands, channel);
			} else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
		}
		String directoryInformation = "Working directory: ~" + currentDirectory + "\r\n";
		channel.write(ByteBuffer.wrap(directoryInformation.getBytes(StandardCharsets.UTF_8)));

		// вывод nickname в начале строки
		String nickReady = nickname + ": ";
		channel.write(ByteBuffer.wrap(nickReady.getBytes(StandardCharsets.UTF_8)));
	}


	private String getFileList() {
		return String.join(" ", new File("server").list());
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel) key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	// nick [name] - смена никнейма
	private void changeNickname(String[] com, SocketChannel channel) throws IOException {
		String emptyNick = "Nickname cannot be empty\r\n";
		try {
			nickname = com[1];
		} catch (IndexOutOfBoundsException e) {
			channel.write(ByteBuffer.wrap(emptyNick.getBytes(StandardCharsets.UTF_8)));
		}
	}

	// touch [filename] - создание файла
	private void createFile(String[] com) {
		try {
			try {
				Path newFile = Paths.get(currentDirectory + com[1]);
				if (!Files.exists(newFile))
					Files.createFile(newFile);
			} catch (RuntimeException e) {
				Path newFile = Paths.get(currentDirectory + "/new file(" + numberOfNewFiles + ")");
				numberOfNewFiles++;
				Files.createFile(newFile);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// mkdir [dirname] - создание директории
	private void createDirectory(String[] com) {
		try {
			try {
				Path newDirectory = Paths.get(currentDirectory + com[1]);
				if (!Files.exists(newDirectory))
					Files.createDirectory(newDirectory);
			} catch (RuntimeException e) {
				Path newDirectory = Paths.get(currentDirectory + "New folder (" + numberOfNewFolders + ")");
				numberOfNewFolders++;
				Files.createDirectory(newDirectory);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// cat [filename] - просмотр содержимого
	private void showFile(String[] com, SocketChannel channel) throws IOException {
		String readyLine;
		String emptyFile = "Filename is empty\r\n";
		try {
			Path pathToFile = Paths.get(currentDirectory + com[1]);
			for (String line : Files.readAllLines(pathToFile)) {
				readyLine = line + "\r\n";
				channel.write(ByteBuffer.wrap(readyLine.getBytes(StandardCharsets.UTF_8)));
			}
		} catch (RuntimeException e) {
			channel.write(ByteBuffer.wrap(emptyFile.getBytes(StandardCharsets.UTF_8)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// rm [filename | dirname] - удаление файла или папки
	private void deleteFile(String[] com, SocketChannel channel) throws IOException {
		String emptyFile = "Filename or directory's name is empty\r\n";
		try {
			Path pathToFile = Paths.get(currentDirectory + com[1]);
			Files.delete(pathToFile);
		} catch (RuntimeException e) {
			channel.write(ByteBuffer.wrap(emptyFile.getBytes(StandardCharsets.UTF_8)));
		}
	}

	// copy [src] [target] - копирование файла или папки
	private void copyFile(String[] com, SocketChannel channel) throws IOException {
		String emptyFile = "Filename or directory's name cannot be empty\r\n";
		try {
			Path pathToFile = Paths.get(currentDirectory + com[1]);
			Path output = Paths.get(currentDirectory + com[2] + "/" + com[1]);
			if (!Files.exists(output))
				Files.copy(pathToFile, output, StandardCopyOption.COPY_ATTRIBUTES);
		} catch (RuntimeException e) {
			channel.write(ByteBuffer.wrap(emptyFile.getBytes(StandardCharsets.UTF_8)));
		}
	}

	// cd [path] - перемещение по каталогу (.. | ~ )
	private void changeDirectory(String[] com, SocketChannel channel) throws IOException {
		String emptyDirectory = "Directory's name is empty";
		try {
			if ("~".equals(com[1])) {
				currentDirectory = "server/";
			} else if ("..".equals(com[1]) && !currentDirectory.equals("server/")) {
				StringBuilder sb = new StringBuilder();
				String[] comma = currentDirectory.split("/");
				for (int i = 0; i < comma.length - 1; i++) {
					sb.append(comma[i] + "/");
				}
				currentDirectory = sb.toString();
			} else if (Files.exists(Paths.get(currentDirectory + com[1])) && !"..".equals(com[1])) {
				currentDirectory = currentDirectory + com[1] + "/";
			}
		} catch (RuntimeException e) {
			channel.write(ByteBuffer.wrap(emptyDirectory.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\r\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\r\n".getBytes(StandardCharsets.UTF_8)));
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}