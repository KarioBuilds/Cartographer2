package io.github.bananapuncher714.cartographer.core.implementation.v1_12_R1;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursor.Type;

import io.github.bananapuncher714.cartographer.core.Cartographer;
import io.github.bananapuncher714.cartographer.core.api.GeneralUtil;
import io.github.bananapuncher714.cartographer.core.api.PacketHandler;
import io.github.bananapuncher714.cartographer.core.internal.Util_1_8;
import io.github.bananapuncher714.cartographer.core.map.palette.MinimapPalette;
import io.github.bananapuncher714.cartographer.core.util.CrossVersionMaterial;
import io.github.bananapuncher714.cartographer.core.util.MapUtil;
import io.github.bananapuncher714.cartographer.tinyprotocol.TinyProtocol;
import io.netty.channel.Channel;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.EnumRenderType;
import net.minecraft.server.v1_12_R1.IBlockAccess;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.MapIcon;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.PacketPlayInBlockDig;
import net.minecraft.server.v1_12_R1.PacketPlayInBlockDig.EnumPlayerDigType;
import net.minecraft.server.v1_12_R1.PacketPlayOutMap;

public class NMSHandler implements PacketHandler {
	private static Field[] MAP_FIELDS = new Field[ 9 ];
	private static Map< MapCursor.Type, MapIcon.Type > CURSOR_TYPES = new EnumMap< MapCursor.Type, MapIcon.Type >( MapCursor.Type.class );
	private static Field SIMPLECOMMANDMAP_COMMANDS;
	
	static {
		try {
			MAP_FIELDS[ 0 ] = PacketPlayOutMap.class.getDeclaredField( "a" );
			MAP_FIELDS[ 1 ] = PacketPlayOutMap.class.getDeclaredField( "b" );
			MAP_FIELDS[ 2 ] = PacketPlayOutMap.class.getDeclaredField( "c" );
			MAP_FIELDS[ 3 ] = PacketPlayOutMap.class.getDeclaredField( "d" );
			MAP_FIELDS[ 4 ] = PacketPlayOutMap.class.getDeclaredField( "e" );
			MAP_FIELDS[ 5 ] = PacketPlayOutMap.class.getDeclaredField( "f" );
			MAP_FIELDS[ 6 ] = PacketPlayOutMap.class.getDeclaredField( "g" );
			MAP_FIELDS[ 7 ] = PacketPlayOutMap.class.getDeclaredField( "h" );
			MAP_FIELDS[ 8 ] = PacketPlayOutMap.class.getDeclaredField( "i" );

			for ( Field field : MAP_FIELDS ) {
				field.setAccessible( true );
			}
			
			SIMPLECOMMANDMAP_COMMANDS = SimpleCommandMap.class.getDeclaredField( "knownCommands" );
			SIMPLECOMMANDMAP_COMMANDS.setAccessible( true );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}
		
		CURSOR_TYPES.put( MapCursor.Type.WHITE_POINTER, MapIcon.Type.PLAYER );
		CURSOR_TYPES.put( MapCursor.Type.GREEN_POINTER, MapIcon.Type.FRAME );
		CURSOR_TYPES.put( MapCursor.Type.RED_POINTER, MapIcon.Type.PLAYER_OFF_LIMITS );
		CURSOR_TYPES.put( MapCursor.Type.BLUE_POINTER, MapIcon.Type.BLUE_MARKER );
		CURSOR_TYPES.put( MapCursor.Type.WHITE_CROSS, MapIcon.Type.TARGET_X );
		CURSOR_TYPES.put( MapCursor.Type.RED_MARKER, MapIcon.Type.RED_MARKER );
		CURSOR_TYPES.put( MapCursor.Type.WHITE_CIRCLE, MapIcon.Type.PLAYER );
		CURSOR_TYPES.put( MapCursor.Type.SMALL_WHITE_CIRCLE, MapIcon.Type.PLAYER_OFF_MAP );
		CURSOR_TYPES.put( MapCursor.Type.MANSION, MapIcon.Type.MANSION );
		CURSOR_TYPES.put( MapCursor.Type.TEMPLE, MapIcon.Type.MONUMENT );
	}

	private final Set< Integer > maps = new TreeSet< Integer >();
	private Util_1_8 util = new Util_1_8();
	
	@Override
	public void sendDataTo( int id, byte[] data, @Nullable MapCursor[] cursors, UUID... uuids ) {
		MapIcon[] icons;
		if ( cursors == null ) {
			icons = new MapIcon[ 0 ];
		} else {
			icons = new MapIcon[ cursors.length ];
			
			for ( int index = 0; index < cursors.length; index++ ) {
				MapCursor cursor = cursors[ index ];
				
				icons[ index ] = new MapIcon( CURSOR_TYPES.get( cursor.getType() ), cursor.getX(), cursor.getY(), cursor.getDirection() );
			}
		}
		
		PacketPlayOutMap packet = new PacketPlayOutMap();
		
		try {
			MAP_FIELDS[ 0 ].set( packet, id );
			MAP_FIELDS[ 1 ].set( packet, ( byte ) 0 );
			MAP_FIELDS[ 2 ].set( packet, false );
			MAP_FIELDS[ 3 ].set( packet, icons );
			MAP_FIELDS[ 4 ].set( packet, 0 );
			MAP_FIELDS[ 5 ].set( packet, 0 );
			MAP_FIELDS[ 6 ].set( packet, 128 );
			MAP_FIELDS[ 7 ].set( packet, 128 );
			MAP_FIELDS[ 8 ].set( packet, data );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}
		
		PacketPlayOutMinimap mapPacket = new PacketPlayOutMinimap( packet );
		
		TinyProtocol protocol = Cartographer.getInstance().getProtocol();
		for ( UUID uuid : uuids ) {
			if ( uuid != null ) {
				Channel channel = protocol.getChannel( uuid, null );
				if ( channel != null ) {
					protocol.sendPacket( channel, mapPacket );
				}
			}
		}
	}

	@Override
	public Object onPacketInterceptOut( Player viewer, Object packet ) {
		if ( packet instanceof PacketPlayOutMinimap ) {
			return ( ( PacketPlayOutMinimap ) packet ).packet;
		} else if ( packet instanceof PacketPlayOutMap ) {
			if ( packet.getClass().equals( PacketPlayOutMap.class ) ) {
				try {
					int id = MAP_FIELDS[ 0 ].getInt( packet );
					if ( maps.contains( id ) ) {
						return null;
					}
				} catch ( IllegalArgumentException | IllegalAccessException e ) {
					e.printStackTrace();
				}
			}
		}
		return packet;
	}
	
	@Override
	public Object onPacketInterceptIn( Player viewer, Object packet ) {
		if ( packet instanceof PacketPlayInBlockDig ) {
			// Check for the drop packet
			PacketPlayInBlockDig digPacket = ( PacketPlayInBlockDig ) packet;

			EnumPlayerDigType type = digPacket.c();
			if ( type == EnumPlayerDigType.DROP_ALL_ITEMS || type == EnumPlayerDigType.DROP_ITEM ) {
				ItemStack item = viewer.getEquipment().getItemInMainHand();
				if ( Cartographer.getInstance().getMapManager().isMinimapItem( item ) ) {
					// Update the player's hand
					viewer.getEquipment().setItemInMainHand( item );
					
					// Activate the drop
					Cartographer.getInstance().getMapManager().activateDrop( viewer, type == EnumPlayerDigType.DROP_ALL_ITEMS );
					return null;
				}
			}
		}
		return packet;
	}
	
	@Override
	public boolean isMapRegistered( int id ) {
		return maps.contains( id );
	}
	
	@Override
	public void registerMap( int id ) {
		maps.add( id );
	}
	
	@Override
	public void unregisterMap( int id ) {
		maps.remove( id );
	}
	
	@Override
	public MapCursor constructMapCursor( int x, int y, double yaw, Type cursorType, String name ) {
		return new MapCursor( ( byte ) x, ( byte ) y, MapUtil.getDirection( yaw ), cursorType, true );
	}
	
	@Override
	public MinimapPalette getVanillaPalette() {
		MinimapPalette palette = new MinimapPalette();
		for ( MinecraftKey key : Block.REGISTRY.keySet() ) {
			Block block = Block.REGISTRY.get( key );
			for ( IBlockData data : block.s().a() ) {
				boolean transparent = data.i() == EnumRenderType.INVISIBLE;
				CrossVersionMaterial material = new CrossVersionMaterial( CraftMagicNumbers.getMaterial( block ), block.toLegacyData( data ) );
				
				if ( transparent ) {
					palette.addTransparentMaterial( material );
				} else {
					try {
						int color = block.c( data, ( IBlockAccess ) null, null ).ac;
						palette.setColor( material, color );
					} catch ( NullPointerException exception ) {
						// Looks like one of the arguments wasn't meant to be null
					}
				}
			}
		}
		return palette;
	}
	
	@Override
	public double getTPS() {
		return MinecraftServer.getServer().recentTps[ 0 ];
	}
	
	@Override
	public boolean registerCommand( PluginCommand command ) {
		Validate.notNull( command );
		return registerCommand( command.getPlugin().getName(), command );
	}
	
	@Override
	public boolean registerCommand( String fallbackPrefix, PluginCommand command ) {
		Validate.notNull( fallbackPrefix );
		Validate.notNull( command );		
		return ( ( CraftServer ) Bukkit.getServer() ).getCommandMap().register( command.getPlugin().getName(), command );
	}
	
	@Override
	public void unregisterCommand( PluginCommand command ) {
		Validate.notNull( command );
		try {
			SimpleCommandMap map = ( ( CraftServer ) Bukkit.getServer() ).getCommandMap();
			Map< String, Command > commands = ( Map< String, Command > ) SIMPLECOMMANDMAP_COMMANDS.get( map );
			for ( Iterator< Entry< String, Command > > iterator = commands.entrySet().iterator(); iterator.hasNext(); ) {
				Entry< String, Command > entry = iterator.next();
				if ( entry.getValue() == command ) {
					iterator.remove();
				}
			}
		} catch ( IllegalArgumentException | IllegalAccessException e ) {
			e.printStackTrace();
		}
	}
	
	@Override
	public GeneralUtil getUtil() {
		return util;
	}
	
	private class PacketPlayOutMinimap extends PacketPlayOutMap {
		protected final PacketPlayOutMap packet;
		
		protected PacketPlayOutMinimap( PacketPlayOutMap packet ) {
			this.packet = packet;
		}
	}
}
