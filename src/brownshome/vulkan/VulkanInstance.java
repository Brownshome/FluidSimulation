package brownshome.vulkan;

import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

public class VulkanInstance {
	private VkInstance instance;
	private VkDevice device;

	private MemoryStack creationStack = MemoryStack.create();

	{ creationStack.push(); }

	private final Collection<Long> extensions = new ArrayList<>();
	private final VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.callocStack(creationStack);

	private final VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(creationStack);

	private VkDebugReportCallbackEXTI callback;
	private VkDebugReportCallbackCreateInfoEXT debugCreateInfo;
	private long debugCallbackhandle;

	private Map<Integer, FloatBuffer> prioritiesMap = new HashMap<>();

	private long surface;

	private VkPhysicalDevice physicalDevice;
	
	private long swapchain;
	private int imageFormat;
	private LongBuffer imageHandles;
	private LongBuffer imageViewHandles;
	
	public VulkanInstance(String appName, int appVersion, String engineName, int engineVersion, int vulkanVersion) {
		deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);

		VkApplicationInfo appInfo = VkApplicationInfo.callocStack(creationStack).set(
				VK_STRUCTURE_TYPE_APPLICATION_INFO, 
				NULL, 
				creationStack.UTF8(appName),
				appVersion, 
				creationStack.UTF8(engineName), 
				engineVersion, 
				vulkanVersion);

		instanceCreateInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
		instanceCreateInfo.pApplicationInfo(appInfo);
	}

	public VulkanInstance debug(VkDebugReportCallbackEXTI callback) {
		this.callback = callback;

		debugCreateInfo = VkDebugReportCallbackCreateInfoEXT.callocStack(creationStack);
		debugCreateInfo.set(
				VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT, 
				NULL, 
				VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT, 
				callback, 
				NULL);

		return this;
	}

	public VulkanInstance withLayers(String... layers) {
		PointerBuffer names = creationStack.mallocPointer(layers.length);
		for(String name : layers) {
			names.put(creationStack.UTF8(name));
		}
		names.flip();

		instanceCreateInfo.ppEnabledLayerNames(names);

		return this;
	}

	public VulkanInstance withInstanceExtension(String extension) {
		extensions.add(memAddress(creationStack.UTF8(extension)));
		
		return this;
	}
	
	public VulkanInstance withInstanceExtensions(PointerBuffer strings) {
		while(strings.hasRemaining()) {
			extensions.add(strings.get());
		}
		
		strings.flip();
		
		return this;
	}

	public int getFamily(int requiredFlags, VkPhysicalDevice device) {
		int i = 0;
		for(VkQueueFamilyProperties prop : queueFamilyList(device)) {
			IntBuffer bool = stackMallocInt(1);
			vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, bool);
			if(bool.get() == 0)
				continue;
			
			if((prop.queueFlags() & requiredFlags) == requiredFlags) {
				return i;
			}
			
			i++;
		}
		
		throw new VulkanException("No queue found");
	}
	
	public VulkanInstance withQueue(int family, float... priorities) {
		assert priorities.length != 0;
		assert !prioritiesMap.containsKey(family);
		
		FloatBuffer prioritiesBuffer = creationStack.floats(priorities);
		prioritiesMap.put(family, prioritiesBuffer);
		return this;
	}

	//This must be called before querying queues or devices.
	public VulkanInstance initializeInstance() {
		try(MemoryStack stack = stackPush()) {
			
			PointerBuffer extensionNames = stackMallocPointer(extensions.size());
			for(long pointer : extensions) {
				extensionNames.put(pointer);
			}
			extensionNames.flip();
			
			instanceCreateInfo.ppEnabledExtensionNames(extensionNames);
			
			PointerBuffer buffer = stackMallocPointer(1);
			int result = vkCreateInstance(instanceCreateInfo, null, buffer);
			if(result != VK_SUCCESS) {
				throw new IllegalStateException("Unable to create vulkan instance");
			}

			instance = new VkInstance(buffer.get(), instanceCreateInfo);

			LongBuffer returnBuffer = stackMallocLong(1);
			vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, returnBuffer);
			debugCallbackhandle = returnBuffer.get();

			return this;
		}
	}

	public VulkanInstance withDeviceExtensions(String... extensions) {
		PointerBuffer names = creationStack.mallocPointer(extensions.length);
		for(String name : extensions) {
			names.put(creationStack.UTF8(name));
		}
		names.flip();

		deviceCreateInfo.ppEnabledExtensionNames(names);

		return this;
	}

	public VkPhysicalDeviceFeatures getDeviceFeatures(VkPhysicalDevice physicalDevice) {
		VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.callocStack(creationStack);
		vkGetPhysicalDeviceFeatures(physicalDevice, features);
		return features;
	}

	public VulkanInstance withDeviceFeatures(VkPhysicalDeviceFeatures features) {
		deviceCreateInfo.pEnabledFeatures(features);
		return this;
	}
	
	public VulkanInstance createSwapChain(int width, int height) {
		try(MemoryStack stack = stackPush()) {
			IntBuffer countPtr = stackMallocInt(1);
			vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, countPtr, null);
			int count = countPtr.get();
			
			countPtr.rewind();
			VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.mallocStack(count);
			vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, countPtr, formats);
			
			countPtr.rewind();
			vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, countPtr, null);
			count = countPtr.get();
			
			countPtr.rewind();
			IntBuffer presentModes = stackMallocInt(count);
			vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, countPtr, presentModes);
			
			VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.mallocStack();
			vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);
			
			VkSurfaceFormatKHR chosenFormat = null;
			for(VkSurfaceFormatKHR format : formats) {
				if(format.format() == VK_FORMAT_UNDEFINED) {
					chosenFormat = format;
					break;
				}
				
				if(format.format() == VK_FORMAT_R8G8B8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
					chosenFormat = format;
					break;
				}
				
				chosenFormat = format;
			}
			
			if(chosenFormat == null)
				throw new VulkanException("No suitable colour format was found");
			
			int chosenMode = VK_PRESENT_MODE_FIFO_KHR;
			while(presentModes.hasRemaining()) {
				int mode = presentModes.get();
				if(mode == VK_PRESENT_MODE_MAILBOX_KHR) {
					chosenMode = VK_PRESENT_MODE_MAILBOX_KHR;
					break;
				}
			}
			
			VkExtent2D actualExtent;
			if(capabilities.currentExtent().width() != -1) {
				actualExtent = capabilities.currentExtent();
			} else {
				actualExtent = VkExtent2D.mallocStack();

				if(Integer.compareUnsigned(width, capabilities.minImageExtent().width()) < 0) {
					width = capabilities.minImageExtent().width();
				} else if(Integer.compareUnsigned(capabilities.maxImageExtent().width(), width) < 0) {
					width = capabilities.maxImageExtent().width();
				}
				
				if(Integer.compareUnsigned(height, capabilities.minImageExtent().height()) < 0) {
					height = capabilities.minImageExtent().height();
				} else if(Integer.compareUnsigned(capabilities.maxImageExtent().height(), height) < 0) {
					height = capabilities.maxImageExtent().height();
				}
			}
			
			int imageCount = capabilities.minImageCount() + 1;
			
			if(capabilities.minImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
				imageCount = capabilities.maxImageCount();
			}
			
			VkSwapchainCreateInfoKHR swapChainCreateInfo = VkSwapchainCreateInfoKHR.callocStack();
			swapChainCreateInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
			.minImageCount(imageCount)
			.imageFormat(chosenFormat.format())
			.imageColorSpace(chosenFormat.colorSpace())
			.imageExtent(actualExtent)
			.imageArrayLayers(1)
			.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
			.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
			.preTransform(capabilities.currentTransform())
			.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
			.presentMode(chosenMode)
			.clipped(true)
			.surface(surface)
			.oldSwapchain(VK_NULL_HANDLE);
			
			LongBuffer swapChainPtr = stackMallocLong(1);
			check(vkCreateSwapchainKHR(device, swapChainCreateInfo, null, swapChainPtr));
			swapchain = swapChainPtr.get();
			
			IntBuffer imageCountPtr = stackMallocInt(1);
			vkGetSwapchainImagesKHR(device, swapchain, imageCountPtr, null);
			int swapchainImageCount = imageCountPtr.get();
			imageCountPtr.rewind();
			imageHandles = BufferUtils.createLongBuffer(swapchainImageCount);
			vkGetSwapchainImagesKHR(device, swapchain, imageCountPtr, imageHandles);
			
			return this;
		}
	}
	
	public void createImageViews() {
		try(MemoryStack stack = stackPush()) {
			imageViewHandles = BufferUtils.createLongBuffer(imageHandles.remaining());
			
			while(imageHandles.hasRemaining()) {
				long handle = imageHandles.get();
			
				VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.mallocStack();
				createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
					.image(handle)
					.viewType(VK_IMAGE_VIEW_TYPE_2D)
					.format(imageFormat);
				
				createInfo.subresourceRange()
					.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.baseMipLevel(0)
					.levelCount(1)
					.baseArrayLayer(0)
					.layerCount(1);
				
				createInfo.components()
					.r(VK_COMPONENT_SWIZZLE_IDENTITY)
					.g(VK_COMPONENT_SWIZZLE_IDENTITY)
					.b(VK_COMPONENT_SWIZZLE_IDENTITY)
					.a(VK_COMPONENT_SWIZZLE_IDENTITY);
				
				check(vkCreateImageView(device, createInfo, null, imageViewHandles));
			}
			
			imageHandles.rewind();
		}
	}
	
	public Iterable<VkPhysicalDevice> deviceList() {
		assert instance != null;

		try(MemoryStack stack = stackPush()) {
			IntBuffer countPtr = stackMallocInt(1);
			check(vkEnumeratePhysicalDevices(instance, countPtr, null));
			int count = countPtr.get();
			countPtr.rewind();

			PointerBuffer devices = creationStack.mallocPointer(count);
			check(vkEnumeratePhysicalDevices(instance, countPtr, devices));

			return () -> new Iterator<VkPhysicalDevice>() {
				PointerBuffer clone = devices.duplicate();

				@Override public boolean hasNext() {
					return clone.hasRemaining();
				}

				@Override
				public VkPhysicalDevice next() {
					return new VkPhysicalDevice(clone.get(), instance);
				}
			};
		}
	}

	public void check(int result) {
		if(result != VK_SUCCESS) {
			throw new VulkanException(result);
		}
	}

	public Iterable<VkQueueFamilyProperties> queueFamilyList(VkPhysicalDevice physicalDevice) {
		try(MemoryStack stack = stackPush()) {
			IntBuffer countPtr = stackMallocInt(1);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, countPtr, null);
			int count = countPtr.get();
			countPtr.rewind();

			VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.mallocStack(count, creationStack);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, countPtr, families);

			return families;
		}
	}
	
	public void initializeLogicalDevice(VkPhysicalDevice physicalDevice) {
		try(MemoryStack stack = stackPush()) {
			VkDeviceQueueCreateInfo.Buffer queueRequests = VkDeviceQueueCreateInfo.callocStack(prioritiesMap.size());
			for(Map.Entry<Integer, FloatBuffer> entry : prioritiesMap.entrySet()) {
				queueRequests.get().set(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, NULL, 0, entry.getKey(), entry.getValue());
			}
			
			queueRequests.flip();
			deviceCreateInfo.pQueueCreateInfos(queueRequests);
			
			PointerBuffer devicePtr = stackMallocPointer(1);
			check(vkCreateDevice(physicalDevice, deviceCreateInfo, null, devicePtr));
			device = new VkDevice(devicePtr.get(), physicalDevice, deviceCreateInfo);
			
			this.physicalDevice = physicalDevice;
		}
	}
	
	public VkQueue getQueue(int family, int n) {
		try(MemoryStack stack = stackPush()) {
			PointerBuffer buffer = stackMallocPointer(1);
			vkGetDeviceQueue(device, family, n, buffer);
			return new VkQueue(buffer.get(), device);
		}
	}

	public void finishInitialization() {
		creationStack = null; //let the GC do its job
	}

	public VkInstance vkInstance() {
		return instance;
	}

	public VulkanInstance withSurface(long l) {
		surface = l;
		return this;
	}
}
