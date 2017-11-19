package brownshome.fluid2d.gpu;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDebugReportCallbackCreateInfoEXT;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import brownshome.vulkan.VulkanException;
import brownshome.vulkan.VulkanInstance;

import org.lwjgl.vulkan.VkDebugReportCallbackEXT;
import org.lwjgl.vulkan.VkDebugReportCallbackEXTI;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;

public class GPUFluidSimulation {
	public static void main(String[] args) {
		GPUFluidSimulation sim = new GPUFluidSimulation();

		sim.createWindow();
		sim.initVulkan();
		sim.mainLoop();
		sim.cleanup();
	}

	private long windowPointer;
	private VulkanInstance vk;

	private void createWindow() {
		glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));

		if(!glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

		windowPointer = glfwCreateWindow(800, 600, "Fluid Simulation", NULL, NULL);
		if(windowPointer == NULL) {
			throw new IllegalStateException("Unable to create window");
		}
	}

	private void initVulkan() {
		try(MemoryStack stack = stackPush()) {
			vk = new VulkanInstance("Fluid Dynamics", VK_MAKE_VERSION(0, 0, 0), "Custom", VK_MAKE_VERSION(0, 0, 0), VK_API_VERSION_1_0);

			vk.withInstanceExtensions(glfwGetRequiredInstanceExtensions())
			.withInstanceExtension(VK_EXT_DEBUG_REPORT_EXTENSION_NAME)
			.withLayers("VK_LAYER_LUNARG_standard_validation")
			.debug((flags, objectType, object, location, messageCode, layerPrefix, message, userData) -> {
				System.err.println(memUTF8(message));
				return VK_TRUE;
			}).initializeInstance();

			Iterable<VkPhysicalDevice> devices = vk.deviceList();
			
			int bestScore = 0;
			VkPhysicalDevice bestDevice = null;
			for(VkPhysicalDevice device : devices) {
				int score = rateDevice(device);
				
				if(score > bestScore) {
					bestScore = score;
					bestDevice = device;
				}
			}
			
			if(bestDevice == null)
				throw new VulkanException("No acceptable device found");
			
			VkPhysicalDeviceFeatures requiredFeatures = VkPhysicalDeviceFeatures.callocStack();
			requiredFeatures.geometryShader(true);
			vk.withDeviceFeatures(requiredFeatures);
			
			vk.withDeviceExtensions(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
			
			LongBuffer surfacePtr = stackMallocLong(1);
			vk.check(glfwCreateWindowSurface(vk.vkInstance(), windowPointer, null, surfacePtr));
			vk.withSurface(surfacePtr.get());
			
			int family = vk.getFamily(VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT, bestDevice);
			
			vk.withQueue(family, 1.0f);
			
			vk.initializeLogicalDevice(bestDevice);
			
			vk.createSwapChain(800, 600);
			vk.createImageViews();
		}
	}

	private int rateDevice(VkPhysicalDevice device) {
		try(MemoryStack stack = stackPush()) {	
			VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.mallocStack();
			vkGetPhysicalDeviceProperties(device, properties);
			
			int score = 0;
			
			switch(properties.deviceType()) {
				case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: score += 100;
				case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: score += 100;
				case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: score += 100;
				case VK_PHYSICAL_DEVICE_TYPE_CPU: score += 100;
			}
			
			score += properties.limits().maxImageDimension2D();
			
			VkPhysicalDeviceFeatures features = vk.getDeviceFeatures(device);
			if(!features.geometryShader()) {
				return 0;
			}
			
			return score;
		}
	}

	private void mainLoop() {
		while(!glfwWindowShouldClose(windowPointer)) {
			glfwPollEvents();
		}
	}

	private void cleanup() {
		
	}
}
